package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.DreamHomeSetRequest;
import com.jipjung.project.controller.dto.request.SavingsRecordRequest;
import com.jipjung.project.controller.dto.response.DreamHomeSetResponse;
import com.jipjung.project.controller.dto.response.GoalExpProgressResponse;
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
import java.util.Objects;

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
    private final StreakService streakService;
    private final CollectionService collectionService;

    // EXP 정책은 ExpPolicy로 통합 관리합니다.

    // =========================================================================
    // Public API Methods
    // =========================================================================

    /**
     * 드림홈 설정 (V2)
     * <p>
     * V2 리팩토링: 저축 목표와 매물 참조를 분리하여 처리
     * <ul>
     *   <li>신규 생성: targetAmount 필수, aptSeq 선택</li>
     *   <li>기존 업데이트: aptSeq 변경은 자유, targetAmount 변경은 저축 진행 전에만 허용</li>
     * </ul>
     *
     * @param userId  사용자 ID
     * @param request 드림홈 설정 요청
     * @return 설정된 드림홈 정보
     */
    @Transactional
    public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
        // 1. 매물 조회 (선택 - V2에서 nullable 허용)
        String normalizedAptSeq = request.normalizedAptSeq();
        Apartment apartment = normalizedAptSeq != null
                ? findApartmentOrThrow(normalizedAptSeq)
                : null;

        // 2. 테마 검증 및 저장
        if (request.getThemeId() != null) {
            validateAndSaveTheme(userId, request.getThemeId());
        }

        // 3. 기존 활성 드림홈 조회
        DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);

        // 4. 신규 생성 vs 기존 업데이트 분기
        DreamHome dreamHome;
        if (existingDreamHome != null) {
            dreamHome = updateExistingDreamHomeV2(existingDreamHome, request);
        } else {
            dreamHome = createNewDreamHome(userId, request, request.getTargetAmount());
        }

        Apartment responseApartment = apartment;
        if (responseApartment == null && dreamHome.getAptSeq() != null) {
            responseApartment = findApartmentOrThrow(dreamHome.getAptSeq());
        }
        Long latestDealPrice = resolveLatestDealPrice(responseApartment);

        return DreamHomeSetResponse.from(dreamHome, responseApartment, latestDealPrice);
    }

    @Transactional
    public SavingsRecordResponse recordSavings(Long userId, SavingsRecordRequest request) {
        DreamHome dreamHome = findActiveDreamHomeOrThrow(userId);

        saveSavingsHistory(dreamHome.getDreamHomeId(), request);
        long newSavedAmount = updateSavedAmount(dreamHome, request);

        ExpLevelResult expResult = processExpAndLevel(userId, request);

        // 입금(DEPOSIT) 시에만 스트릭 자동 참여
        StreakService.StreakResult streakResult = null;
        if (request.saveType() == SaveType.DEPOSIT) {
            streakResult = streakService.participate(userId, ActivityType.SAVINGS);
        }

        CollectionService.GoalCompletionResult completionResult =
                collectionService.checkAndUpdateCompletionByExp(userId, dreamHome, newSavedAmount);

        return buildSavingsResponse(dreamHome, newSavedAmount, completionResult, expResult, streakResult);
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

    /**
     * V2: 기존 드림홈 부분 업데이트
     * <p>
     * 저축 목표와 매물 참조를 독립적으로 처리:
     * <ul>
     *   <li>aptSeq 변경: apt_seq만 업데이트 (저축 진행 상태에 영향 없음)</li>
     *   <li>targetAmount 변경: 저축 진행 중에는 증가만 허용</li>
     * </ul>
     *
     * @param existing  기존 드림홈
     * @param request   업데이트 요청
     * @return 업데이트된 드림홈
     */
    private DreamHome updateExistingDreamHomeV2(
            DreamHome existing,
            DreamHomeSetRequest request
    ) {
        Long dreamHomeId = existing.getDreamHomeId();
        // 1. aptSeq 변경 처리 (저축 목표에 영향 없음)
        if (request.hasAptSeqField()) {
            String newAptSeq = request.normalizedAptSeq();
            if (!Objects.equals(existing.getAptSeq(), newAptSeq)) {
                dreamHomeMapper.updateAptSeq(dreamHomeId, newAptSeq);
                log.info("Property reference updated. dreamHomeId: {}, {} -> {}",
                        dreamHomeId, existing.getAptSeq(), newAptSeq);
            }
        }

        // 2. targetAmount 변경 처리 (저축 진행 중 제한)
        Long newTarget = request.getTargetAmount();
        if (!newTarget.equals(existing.getTargetAmount())) {
            validateTargetAmountChange(existing, newTarget);
            dreamHomeMapper.updateTargetAmount(dreamHomeId, newTarget);
            log.info("Target amount updated. dreamHomeId: {}, {} -> {}",
                    dreamHomeId, existing.getTargetAmount(), newTarget);
        }

        // 3. 기타 필드 업데이트 (targetDate, monthlyGoal, houseName)
        if (hasOtherFieldChanges(existing, request)) {
            dreamHomeMapper.updateDreamHomeDetails(
                    dreamHomeId,
                    request.getTargetDate(),
                    request.getMonthlyGoal(),
                    request.getHouseName()
            );
        }

        // 변경된 정보 반영하여 반환
        return dreamHomeMapper.findById(dreamHomeId);
    }

    /**
     * 저축 목표 변경 검증
     * <p>
     * V2 정책: 저축 진행 중(currentSavedAmount > 0)에는 목표 변경 불가
     *
     * @param existing  기존 드림홈
     * @param newTarget 새 목표 금액
     * @throws BusinessException 저축 진행 중이거나 목표가 저축액보다 작은 경우
     */
    private void validateTargetAmountChange(DreamHome existing, Long newTarget) {
        long currentSaved = nullToZero(existing.getCurrentSavedAmount());
        long currentTarget = nullToZero(existing.getTargetAmount());

        if (currentSaved > 0 && newTarget < currentTarget) {
            throw new BusinessException(ErrorCode.TARGET_CHANGE_NOT_ALLOWED,
                    "저축 진행 중에는 목표를 줄일 수 없습니다. 현재 목표: " + currentTarget);
        }

        if (newTarget < currentSaved) {
            throw new BusinessException(ErrorCode.TARGET_LESS_THAN_SAVED,
                    "저축 목표는 현재 저축액(" + currentSaved + ")보다 작을 수 없습니다.");
        }
    }

    /**
     * targetDate, monthlyGoal, houseName 변경 여부 확인
     */
    private boolean hasOtherFieldChanges(DreamHome existing, DreamHomeSetRequest request) {
        return !Objects.equals(existing.getTargetDate(), request.getTargetDate())
                || !Objects.equals(existing.getMonthlyGoal(), request.getMonthlyGoal())
                || !Objects.equals(existing.getHouseName(), request.getHouseName());
    }

    private DreamHome createNewDreamHome(Long userId, DreamHomeSetRequest request, long targetAmount) {
        DreamHome newDreamHome = buildNewDreamHome(userId, request, targetAmount);
        dreamHomeMapper.insert(newDreamHome);
        log.info("Dream home created. dreamHomeId: {}, aptSeq: {}", newDreamHome.getDreamHomeId(), request.getAptSeq());
        return newDreamHome;
    }

    private DreamHome buildNewDreamHome(Long userId, DreamHomeSetRequest request, Long targetAmount) {
        return DreamHome.builder()
                .userId(userId)
                .aptSeq(request.normalizedAptSeq())
                .targetAmount(targetAmount)
                .targetDate(request.getTargetDate())
                .monthlyGoal(request.getMonthlyGoal())
                .currentSavedAmount(0L)
                .startDate(LocalDate.now())
                .status(DreamHomeStatus.ACTIVE)
                .houseName(request.getHouseName())
                .build();
    }

    // =========================================================================
    // Target Validation (DSR)
    // =========================================================================

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
        return ExpPolicy.calculateSavingsExp(amount);
    }

    // =========================================================================
    // Response Building
    // =========================================================================

    private SavingsRecordResponse buildSavingsResponse(
            DreamHome dreamHome,
            long newSavedAmount,
            CollectionService.GoalCompletionResult completionResult,
            ExpLevelResult expResult,
            StreakService.StreakResult streakResult
    ) {
        CollectionService.GoalProgress goalProgress = collectionService.getGoalProgress(dreamHome.getUserId(), dreamHome);
        GoalExpProgressResponse goalExpProgress = GoalExpProgressResponse.from(goalProgress);
        DreamHome updatedDreamHome = DreamHome.builder()
                .dreamHomeId(dreamHome.getDreamHomeId())
                .currentSavedAmount(newSavedAmount)
                .targetAmount(dreamHome.getTargetAmount())
                .status(completionResult.isCompleted() ? DreamHomeStatus.COMPLETED : DreamHomeStatus.ACTIVE)
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
                streakInfo,
                completionResult.isCompleted(),
                completionResult.justCompleted(),
                completionResult.completedCollectionId(),
                goalExpProgress
        );
    }

    private Long resolveLatestDealPrice(Apartment apartment) {
        if (apartment == null || apartment.getDeals() == null || apartment.getDeals().isEmpty()) {
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
