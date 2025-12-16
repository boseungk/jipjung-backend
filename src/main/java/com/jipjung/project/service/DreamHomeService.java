package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.DreamHomeSetRequest;
import com.jipjung.project.controller.dto.request.SavingsRecordRequest;
import com.jipjung.project.controller.dto.response.DreamHomeSetResponse;
import com.jipjung.project.controller.dto.response.SavingsRecordResponse;
import com.jipjung.project.controller.dto.response.StreakInfo;
import com.jipjung.project.domain.*;
import com.jipjung.project.global.exception.BusinessException;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 드림홈 서비스
 * - 드림홈 설정 및 저축 기록 관리
 * - 저축 시 경험치/레벨 시스템 연동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DreamHomeService {

    private final DreamHomeMapper dreamHomeMapper;
    private final SavingsHistoryMapper savingsHistoryMapper;
    private final ApartmentMapper apartmentMapper;
    private final UserMapper userMapper;
    private final GrowthLevelMapper growthLevelMapper;
    private final HouseThemeMapper houseThemeMapper;
    private final DsrService dsrService;
    private final StreakService streakService;

    // 1만원당 1 EXP (10만원당 10 EXP와 동일 비율이지만, 10만원 미만 저축에도 EXP가 반영되도록 단위를 세분화)
    private static final long EXP_PER_UNIT = 10_000L;
    private static final int EXP_AMOUNT = 1;
    private static final int MAX_EXP_PER_SAVINGS = 500;

    // =========================================================================
    // Public API Methods
    // =========================================================================

    @Transactional
    public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
        Apartment apartment = findApartmentOrThrow(request.aptSeq());
        Long latestDealPrice = resolveLatestDealPrice(apartment);
        long targetAmount = resolveValidatedTargetAmount(userId, request, latestDealPrice);
        DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);

        // 테마 선택 시 검증 및 저장
        if (request.themeId() != null) {
            validateAndSaveTheme(userId, request.themeId());
        }

        DreamHome dreamHome = (existingDreamHome != null)
                ? updateExistingDreamHome(existingDreamHome, request, targetAmount)
                : createNewDreamHome(userId, request, targetAmount);

        return DreamHomeSetResponse.from(dreamHome, apartment, latestDealPrice);
    }

    @Transactional
    public SavingsRecordResponse recordSavings(Long userId, SavingsRecordRequest request) {
        DreamHome dreamHome = findActiveDreamHomeOrThrow(userId);

        saveSavingsHistory(dreamHome.getDreamHomeId(), request);
        long newSavedAmount = updateSavedAmount(dreamHome, request);
        boolean isCompleted = checkAndUpdateCompletion(dreamHome, newSavedAmount, userId);

        ExpLevelResult expResult = processExpAndLevel(userId, request);

        // 입금(DEPOSIT) 시에만 스트릭 자동 참여
        StreakService.StreakResult streakResult = null;
        if (request.saveType() == SaveType.DEPOSIT) {
            streakResult = streakService.participate(userId, ActivityType.SAVINGS);
        }

        return buildSavingsResponse(dreamHome, newSavedAmount, isCompleted, expResult, streakResult);
    }

    // =========================================================================
    // Dream Home Operations
    // =========================================================================

    private Apartment findApartmentOrThrow(String aptSeq) {
        return apartmentMapper.findByAptSeqWithDeals(aptSeq)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APARTMENT_NOT_FOUND));
    }

    private DreamHome findActiveDreamHomeOrThrow(Long userId) {
        DreamHome dreamHome = dreamHomeMapper.findActiveByUserId(userId);
        if (dreamHome == null) {
            throw new ResourceNotFoundException(ErrorCode.DREAM_HOME_NOT_FOUND);
        }
        return dreamHome;
    }

    private DreamHome updateExistingDreamHome(DreamHome existing, DreamHomeSetRequest request, long targetAmount) {
        DreamHome updated = buildDreamHome(
                existing.getDreamHomeId(),
                existing.getUserId(),
                request,
                targetAmount,
                nullToZero(existing.getCurrentSavedAmount())
        );
        dreamHomeMapper.updateDreamHome(updated);
        log.info("Dream home updated. dreamHomeId: {}, aptSeq: {}", updated.getDreamHomeId(), request.aptSeq());
        return updated;
    }

    private DreamHome createNewDreamHome(Long userId, DreamHomeSetRequest request, long targetAmount) {
        DreamHome newDreamHome = buildDreamHome(null, userId, request, targetAmount, 0L);
        dreamHomeMapper.insert(newDreamHome);
        log.info("Dream home created. dreamHomeId: {}, aptSeq: {}", newDreamHome.getDreamHomeId(), request.aptSeq());
        return newDreamHome;
    }

    private DreamHome buildDreamHome(
            Long dreamHomeId,
            Long userId,
            DreamHomeSetRequest request,
            Long targetAmount,
            Long savedAmount
    ) {
        return DreamHome.builder()
                .dreamHomeId(dreamHomeId)
                .userId(userId)
                .aptSeq(request.aptSeq())
                .targetAmount(targetAmount)
                .targetDate(request.targetDate())
                .monthlyGoal(request.monthlyGoal())
                .currentSavedAmount(savedAmount)
                .startDate(LocalDate.now())
                .status(DreamHomeStatus.ACTIVE)
                .build();
    }

    // =========================================================================
    // Target Validation (DSR)
    // =========================================================================

    /**
     * DSR 기반 목표 금액 검증 및 보정
     * <p>
     * 최신 실거래가와 LITE DSR 한도를 기준으로 최소 필요 자기자본을 계산합니다.
     * - 실거래가가 없으면 요청값을 그대로 사용
     * - 요청 금액이 부족하면 필요한 금액으로 보정
     */
    private long resolveValidatedTargetAmount(Long userId, DreamHomeSetRequest request, Long latestDealPrice) {
        long requestedTarget = nullToZero(request.targetAmount());

        if (latestDealPrice == null) {
            log.info("No deal price found for aptSeq {}. Using requested targetAmount {} as-is.", request.aptSeq(), requestedTarget);
            return requestedTarget;
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        DsrService.LiteDsrSnapshot dsrSnapshot = dsrService.calculateLiteDsrSnapshot(user);
        long maxLoanAmount = dsrSnapshot.result().maxLoanAmount();
        long requiredCapital = Math.max(0, latestDealPrice - maxLoanAmount);
        long resolved = Math.max(requestedTarget, requiredCapital);

        if (resolved != requestedTarget) {
            log.info("Target amount adjusted by DSR. userId: {}, aptSeq: {}, requested: {}, required: {}, resolved: {}",
                    userId, request.aptSeq(), requestedTarget, requiredCapital, resolved);
        }

        return resolved;
    }

    // =========================================================================
    // Theme Operations
    // =========================================================================

    /**
     * 테마 ID 검증 및 사용자에게 저장
     * <p>
     * 테마가 존재하지 않거나 비활성 상태인 경우 예외 발생.
     *
     * @param userId  사용자 ID
     * @param themeId 테마 ID
     * @throws BusinessException 테마가 존재하지 않거나 비활성 상태인 경우
     */
    private void validateAndSaveTheme(Long userId, Integer themeId) {
        HouseTheme theme = houseThemeMapper.findById(themeId);

        if (theme == null) {
            throw new BusinessException(ErrorCode.THEME_NOT_FOUND,
                    "테마를 찾을 수 없습니다: " + themeId);
        }

        if (!Boolean.TRUE.equals(theme.getIsActive())) {
            throw new BusinessException(ErrorCode.THEME_NOT_ACTIVE,
                    "비활성화된 테마입니다: " + themeId);
        }

        int updated = userMapper.updateSelectedTheme(userId, themeId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        log.info("Theme selected. userId: {}, themeId: {}", userId, themeId);
    }

    // =========================================================================
    // Savings Operations
    // =========================================================================

    private void saveSavingsHistory(Long dreamHomeId, SavingsRecordRequest request) {
        SavingsHistory savings = SavingsHistory.builder()
                .dreamHomeId(dreamHomeId)
                .amount(request.amount())
                .saveType(request.saveType())
                .memo(request.memo())
                .build();
        savingsHistoryMapper.insert(savings);
    }

    private long updateSavedAmount(DreamHome dreamHome, SavingsRecordRequest request) {
        long currentSaved = nullToZero(dreamHome.getCurrentSavedAmount());
        long signedAmount = calculateSignedAmount(request.saveType(), request.amount());
        long newSavedAmount = Math.max(0, currentSaved + signedAmount);

        dreamHomeMapper.updateCurrentSavedAmount(dreamHome.getDreamHomeId(), newSavedAmount);
        return newSavedAmount;
    }

    private long calculateSignedAmount(SaveType saveType, Long amount) {
        return saveType == SaveType.DEPOSIT ? amount : -amount;
    }

    private boolean checkAndUpdateCompletion(DreamHome dreamHome, long newSavedAmount, Long userId) {
        long targetAmount = nullToZero(dreamHome.getTargetAmount());
        boolean isCompleted = newSavedAmount >= targetAmount;

        if (isCompleted) {
            dreamHomeMapper.updateStatus(dreamHome.getDreamHomeId(), DreamHomeStatus.COMPLETED);
            log.info("Dream home completed! userId: {}, dreamHomeId: {}", userId, dreamHome.getDreamHomeId());
        }
        return isCompleted;
    }

    // =========================================================================
    // Experience & Level Operations
    // =========================================================================

    private ExpLevelResult processExpAndLevel(Long userId, SavingsRecordRequest request) {
        int expChange = calculateExpChange(request.saveType(), request.amount());
        User user = userMapper.findByIdForUpdate(userId);

        int oldLevel = LevelPolicy.normalizeLevel(user.getCurrentLevel());
        int oldExp = LevelPolicy.normalizeExp(user.getCurrentExp());

        applyExpIfPositive(userId, expChange);

        int newExp = oldExp + expChange;
        int newLevel = LevelPolicy.calculateLevel(newExp);
        boolean isLevelUp = applyLevelUpIfNeeded(userId, oldLevel, newLevel);

        User updatedUser = userMapper.findById(userId);
        GrowthLevel growthLevel = growthLevelMapper.findByLevel(nullToDefault(updatedUser.getCurrentLevel(), 1));

        return new ExpLevelResult(expChange, updatedUser, growthLevel, isLevelUp);
    }

    private void applyExpIfPositive(Long userId, int expChange) {
        if (expChange > 0) {
            userMapper.addExp(userId, expChange);
        }
    }

    private boolean applyLevelUpIfNeeded(Long userId, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel) {
            return false;
        }
        userMapper.updateLevel(userId, newLevel);
        log.info("Level up! userId: {}, {} -> {}", userId, oldLevel, newLevel);
        return true;
    }

    private int calculateExpChange(SaveType saveType, Long amount) {
        if (saveType == SaveType.WITHDRAW) {
            return 0;
        }
        int units = (int) (amount / EXP_PER_UNIT);
        int exp = units * EXP_AMOUNT;
        return Math.min(exp, MAX_EXP_PER_SAVINGS);
    }

    // =========================================================================
    // Response Building
    // =========================================================================

    private SavingsRecordResponse buildSavingsResponse(
            DreamHome dreamHome,
            long newSavedAmount,
            boolean isCompleted,
            ExpLevelResult expResult,
            StreakService.StreakResult streakResult
    ) {
        DreamHome updatedDreamHome = DreamHome.builder()
                .dreamHomeId(dreamHome.getDreamHomeId())
                .currentSavedAmount(newSavedAmount)
                .targetAmount(dreamHome.getTargetAmount())
                .status(isCompleted ? DreamHomeStatus.COMPLETED : DreamHomeStatus.ACTIVE)
                .build();

        // 스트릭 정보 변환 (오늘 첫 참여 시에만 포함)
        StreakInfo streakInfo = null;
        if (streakResult != null && !streakResult.alreadyParticipated()) {
            streakInfo = new StreakInfo(
                    streakResult.currentStreak(),
                    streakResult.maxStreak(),
                    streakResult.expEarned()
            );
        }

        return SavingsRecordResponse.from(
                updatedDreamHome,
                expResult.expChange(),
                expResult.user(),
                expResult.growthLevel(),
                expResult.isLevelUp(),
                streakInfo
        );
    }

    private Long resolveLatestDealPrice(Apartment apartment) {
        if (apartment.getDeals() == null || apartment.getDeals().isEmpty()) {
            return null;
        }
        Long dealAmountNum = apartment.getDeals().get(0).getDealAmountNum();
        return dealAmountNum != null ? dealAmountNum * 10_000 : null;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    private int nullToDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    // =========================================================================
    // Inner Classes
    // =========================================================================

    private record ExpLevelResult(int expChange, User user, GrowthLevel growthLevel, boolean isLevelUp) {}
}
