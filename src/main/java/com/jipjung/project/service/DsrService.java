package com.jipjung.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.controller.dto.request.DsrSimulationRequest;
import com.jipjung.project.controller.dto.response.DsrSimulationResponse;
import com.jipjung.project.controller.dto.response.DsrSimulationResponse.GameUpdate;
import com.jipjung.project.domain.DreamHome;
import com.jipjung.project.domain.DsrCalculationHistory;
import com.jipjung.project.domain.User;
import com.jipjung.project.dsr.*;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.DreamHomeMapper;
import com.jipjung.project.repository.DsrHistoryMapper;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * DSR 서비스
 * <p>
 * LITE/PRO 모드 DSR 계산을 담당.
 * DsrCalculator에 계산 로직을 위임하여 단일 책임 원칙 준수.
 *
 * <h3>모드 설명</h3>
 * <ul>
 *   <li><b>LITE</b>: 온보딩/대시보드용 간편 계산 (표준 설정 사용)</li>
 *   <li><b>PRO</b>: 상세 시뮬레이션 (사용자 입력 기반, 게임 연동)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DsrService {

    private final DsrCalculator dsrCalculator;
    private final DsrSettings dsrSettings;
    private final UserMapper userMapper;
    private final DreamHomeMapper dreamHomeMapper;
    private final DsrHistoryMapper dsrHistoryMapper;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // Public Methods
    // =========================================================================

    /**
     * LITE 모드 DSR 계산 (온보딩/대시보드용)
     * <p>
     * 표준 설정 사용:
     * - 지역: 수도권
     * - 대출 유형: 변동금리
     * - 금리: 4.5%
     * - 만기: 30년
     *
     * @param user 사용자 정보
     * @return DSR 계산 결과
     */
    public DsrResult calculateLiteDsr(User user) {
        return calculateLiteDsrSnapshot(user).result();
    }

    /**
     * LITE 모드 DSR 계산 + 인정소득 컨텍스트 반환
     */
    public LiteDsrSnapshot calculateLiteDsrSnapshot(User user) {
        long annualIncome = resolveAnnualIncome(user);
        int age = resolveAge(user);
        long existingAnnualDebt = resolveExistingAnnualDebt(user);

        DsrInput input = createDsrInput(
                annualIncome,
                age,
                DsrInput.Region.SEOUL_METRO,      // 기본: 수도권
                existingAnnualDebt,
                0L,                                // 전세 없음
                0.0,
                DsrInput.LoanType.VARIABLE,        // 기본: 변동
                dsrSettings.getLiteModeDefaultRate(),
                dsrSettings.getLiteModeDefaultMaturity(),
                DsrInput.LenderType.BANK,
                false
        );

        DsrPolicy policy = DsrPolicy.bankDefault2025H2();
        double incomeMultiplier = policy.getYouthIncomeMultiplier(age);
        long recognizedAnnualIncome = Math.round(annualIncome * incomeMultiplier);
        DsrResult result = dsrCalculator.calculateMaxLoan(input, policy);

        return new LiteDsrSnapshot(result, recognizedAnnualIncome, existingAnnualDebt);
    }

    /**
     * PRO 모드 DSR 시뮬레이션 (상세 입력 + 게임 연동)
     * <p>
     * Phase 2: 게임 갱신, 프로필 업데이트, 이력 저장 포함
     *
     * @param userId  사용자 ID
     * @param request 시뮬레이션 요청
     * @return 시뮬레이션 응답
     * @throws ResourceNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public DsrSimulationResponse simulate(Long userId, DsrSimulationRequest request) {
        User user = findUserOrThrow(userId);
        DreamHome dreamHome = dreamHomeMapper.findActiveByUserId(userId);
        int age = resolveAge(user);

        SimulationPayload simulation = runSimulation(request, age);

        long oldMaxLoan = resolveOldMaxLoan(user);
        GameUpdate gameUpdate = calculateGameUpdate(user, dreamHome, oldMaxLoan, simulation.result().maxLoanAmount());

        // 3. 프로필 업데이트 (연소득, 월 상환액)
        long monthlyDebt = toMonthlyAmount(request.existingAnnualDebtService());
        userMapper.updateFinancialInfo(userId, request.annualIncome(), monthlyDebt);

        // 4. DSR 캐시 업데이트
        userMapper.updateDsrCache(userId, "PRO", simulation.result().maxLoanAmount());

        // 5. 경험치 반영
        applyExperience(userId, gameUpdate);

        // 6. 이력 저장
        saveHistory(userId, simulation.input(), simulation.result(), "PRO");

        // 7. 응답 생성
        log.info("DSR simulation completed. userId: {}, grade: {}, maxLoan: {}, reducedGap: {}, exp: {}",
                userId, simulation.result().grade(), simulation.result().maxLoanAmount(),
                gameUpdate != null ? gameUpdate.reducedGap() : 0,
                gameUpdate != null ? gameUpdate.expGained() : 0);

        return DsrSimulationResponse.from(
                simulation.result(),
                simulation.stressRate(),
                simulation.youthMultiplier(),
                simulation.tip(),
                gameUpdate
        );
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    private User findUserOrThrow(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 연소득 결정: 사용자 입력값 또는 중위소득 fallback
     */
    private long resolveAnnualIncome(User user) {
        if (user.getAnnualIncome() != null && user.getAnnualIncome() > 0) {
            return user.getAnnualIncome();
        }
        return dsrSettings.getMedianIncome();
    }

    /**
     * 나이 결정: birthYear 기반 계산 또는 기본값
     */
    int resolveAge(User user) {
        if (user.getBirthYear() != null) {
            return LocalDate.now().getYear() - user.getBirthYear();
        }
        return dsrSettings.getDefaultAge();
    }

    /**
     * 기존 연간 부채 상환액 결정
     */
    private long resolveExistingAnnualDebt(User user) {
        if (user.getExistingLoanMonthly() == null) {
            return 0L;
        }
        return toAnnualAmount(user.getExistingLoanMonthly());
    }

    /**
     * DsrInput 생성 (공통 경로)
     */
    private DsrInput createDsrInput(
            long annualIncome,
            int age,
            DsrInput.Region region,
            long existingAnnualDebtService,
            long jeonseLoanBalance,
            double jeonseLoanRate,
            DsrInput.LoanType targetLoanType,
            double targetLoanRate,
            int maturityYears,
            DsrInput.LenderType lenderType,
            boolean jeonseIncludedInDsr
    ) {
        return new DsrInput(
                annualIncome,
                age,
                region,
                existingAnnualDebtService,
                jeonseLoanBalance,
                jeonseLoanRate,
                targetLoanType,
                targetLoanRate,
                maturityYears,
                lenderType,
                jeonseIncludedInDsr
        );
    }

    /**
     * PRO 요청 → DsrInput 변환
     */
    private DsrInput buildDsrInput(DsrSimulationRequest request, int age) {
        return createDsrInput(
                request.annualIncome(),
                age,
                DsrInput.Region.valueOf(request.region()),
                request.existingAnnualDebtService(),
                request.jeonseLoanBalanceOrZero(),
                request.jeonseLoanRateOrZero(),
                DsrInput.LoanType.valueOf(request.targetLoanType()),
                request.targetLoanRate(),
                request.maturityYears(),
                DsrInput.LenderType.valueOf(request.lenderTypeOrDefault()),
                request.isJeonseIncludedInDsr()
        );
    }

    private SimulationPayload runSimulation(DsrSimulationRequest request, int age) {
        DsrInput input = buildDsrInput(request, age);
        DsrPolicy policy = DsrPolicy.bankDefault2025H2();
        DsrResult result = dsrCalculator.calculateMaxLoan(input, policy);

        DsrInput.Region region = DsrInput.Region.valueOf(request.region());
        DsrInput.LoanType loanType = DsrInput.LoanType.valueOf(request.targetLoanType());
        double stressRate = dsrCalculator.calculateStressRate(region, loanType, policy);
        double youthMultiplier = policy.getYouthIncomeMultiplier(age);
        String tip = generateTip(loanType, result);

        return new SimulationPayload(input, result, stressRate, youthMultiplier, tip);
    }

    private long resolveOldMaxLoan(User user) {
        return user.getCachedMaxLoanAmount() != null
                ? user.getCachedMaxLoanAmount()
                : calculateLiteDsr(user).maxLoanAmount();
    }

    private void applyExperience(Long userId, GameUpdate gameUpdate) {
        if (gameUpdate != null && gameUpdate.expGained() > 0) {
            userMapper.addExp(userId, gameUpdate.expGained());
        }
    }

    /**
     * 맞춤 팁 생성
     */
    private String generateTip(DsrInput.LoanType currentType, DsrResult result) {
        // 변동금리 사용 시 주기형 추천
        if (currentType == DsrInput.LoanType.VARIABLE) {
            return "💡 주기형 상품으로 변경하면 스트레스 금리가 낮아져 한도가 늘어날 수 있어요!";
        }

        // 등급별 팁
        return switch (result.grade()) {
            case DsrResult.GRADE_WARNING ->
                    "⚠️ DSR 한도에 가까워요. 대출 만기를 늘리거나 기존 대출을 줄이면 여유가 생겨요.";
            case DsrResult.GRADE_RESTRICTED ->
                    "🚫 현재 조건으로는 추가 대출이 어려워요. 기존 대출 상환을 우선 검토해보세요.";
            default ->
                    "✅ 여유있는 DSR 상태입니다. 목표 금액에 맞춰 저축 계획을 세워보세요!";
        };
    }

    /**
     * 게임 갱신 계산 (Phase 2)
     * <p>
     * Spec 공식: requiredSavings = targetAmount - currentAssets - currentSavedAmount - maxLoanAmount
     *
     * @param user       사용자 정보
     * @param dreamHome  활성 목표 (없으면 null)
     * @param oldMaxLoan 이전 대출 한도
     * @param newMaxLoan 새 대출 한도
     * @return 게임 갱신 정보 (목표 없으면 null)
     */
    private GameUpdate calculateGameUpdate(User user, DreamHome dreamHome, long oldMaxLoan, long newMaxLoan) {
        if (dreamHome == null) {
            return null;
        }

        long targetAmount = dreamHome.getTargetAmount() != null ? dreamHome.getTargetAmount() : 0L;
        long currentAssets = user.getCurrentAssets() != null ? user.getCurrentAssets() : 0L;
        long currentSaved = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0L;

        long oldRequired = Math.max(0, targetAmount - currentAssets - currentSaved - oldMaxLoan);
        long newRequired = Math.max(0, targetAmount - currentAssets - currentSaved - newMaxLoan);

        long reducedGap = Math.max(0, oldRequired - newRequired);
        int expGained = reducedGap > 0
                ? Math.min(dsrSettings.getMaxExp(), (int) (reducedGap / dsrSettings.getExpPerUnit()) * dsrSettings.getExpMultiplier())
                : 0;

        return new GameUpdate(reducedGap, expGained);
    }

    /**
     * DSR 계산 이력 저장
     */
    private void saveHistory(Long userId, DsrInput input, DsrResult result, String dsrMode) {
        try {
            DsrCalculationHistory history = DsrCalculationHistory.builder()
                    .userId(userId)
                    .inputJson(objectMapper.writeValueAsString(input))
                    .resultJson(objectMapper.writeValueAsString(result))
                    .dsrMode(dsrMode)
                    .maxLoanAmount(result.maxLoanAmount())
                    .build();
            dsrHistoryMapper.insert(history);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DSR history. userId: {}", userId, e);
            // 이력 저장 실패는 핵심 로직에 영향 없이 로깅만
        }
    }

    /**
     * LITE DSR 계산 컨텍스트 (대시보드 표기용)
     */
    public record LiteDsrSnapshot(
            DsrResult result,
            long recognizedAnnualIncome,
            long existingAnnualDebt
    ) {}

    private long toMonthlyAmount(long annualAmount) {
        return Math.round(annualAmount / 12.0);
    }

    private long toAnnualAmount(long monthlyAmount) {
        return monthlyAmount * 12;
    }

    private record SimulationPayload(
            DsrInput input,
            DsrResult result,
            double stressRate,
            double youthMultiplier,
            String tip
    ) {}
}
