package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.DreamHomeSetRequest;
import com.jipjung.project.controller.dto.request.SavingsRecordRequest;
import com.jipjung.project.controller.dto.response.DreamHomeSetResponse;
import com.jipjung.project.controller.dto.response.SavingsRecordResponse;
import com.jipjung.project.domain.*;
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

    private static final long EXP_PER_UNIT = 100_000L;
    private static final int EXP_AMOUNT = 10;
    private static final int MAX_EXP_PER_SAVINGS = 500;
    private static final int MAX_LEVEL = 7;
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500, 2100};

    // =========================================================================
    // Public API Methods
    // =========================================================================

    @Transactional
    public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
        Apartment apartment = findApartmentOrThrow(request.aptSeq());
        DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);

        DreamHome dreamHome = (existingDreamHome != null)
                ? updateExistingDreamHome(existingDreamHome, request)
                : createNewDreamHome(userId, request);

        Long latestDealPrice = resolveLatestDealPrice(apartment);
        return DreamHomeSetResponse.from(dreamHome, apartment, latestDealPrice);
    }

    @Transactional
    public SavingsRecordResponse recordSavings(Long userId, SavingsRecordRequest request) {
        DreamHome dreamHome = findActiveDreamHomeOrThrow(userId);

        saveSavingsHistory(dreamHome.getDreamHomeId(), request);
        long newSavedAmount = updateSavedAmount(dreamHome, request);
        boolean isCompleted = checkAndUpdateCompletion(dreamHome, newSavedAmount, userId);

        ExpLevelResult expResult = processExpAndLevel(userId, request);

        return buildSavingsResponse(dreamHome, newSavedAmount, isCompleted, expResult);
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

    private DreamHome updateExistingDreamHome(DreamHome existing, DreamHomeSetRequest request) {
        DreamHome updated = buildDreamHome(
                existing.getDreamHomeId(),
                existing.getUserId(),
                request,
                nullToZero(existing.getCurrentSavedAmount())
        );
        dreamHomeMapper.updateDreamHome(updated);
        log.info("Dream home updated. dreamHomeId: {}, aptSeq: {}", updated.getDreamHomeId(), request.aptSeq());
        return updated;
    }

    private DreamHome createNewDreamHome(Long userId, DreamHomeSetRequest request) {
        DreamHome newDreamHome = buildDreamHome(null, userId, request, 0L);
        dreamHomeMapper.insert(newDreamHome);
        log.info("Dream home created. dreamHomeId: {}, aptSeq: {}", newDreamHome.getDreamHomeId(), request.aptSeq());
        return newDreamHome;
    }

    private DreamHome buildDreamHome(Long dreamHomeId, Long userId, DreamHomeSetRequest request, Long savedAmount) {
        return DreamHome.builder()
                .dreamHomeId(dreamHomeId)
                .userId(userId)
                .aptSeq(request.aptSeq())
                .targetAmount(request.targetAmount())
                .targetDate(request.targetDate())
                .monthlyGoal(request.monthlyGoal())
                .currentSavedAmount(savedAmount)
                .startDate(LocalDate.now())
                .status(DreamHomeStatus.ACTIVE)
                .build();
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
        User user = userMapper.findById(userId);

        int oldLevel = nullToDefault(user.getCurrentLevel(), 1);
        int oldExp = nullToDefault(user.getCurrentExp(), 0);

        applyExpIfPositive(userId, expChange);

        int newExp = oldExp + expChange;
        int newLevel = calculateNewLevel(newExp);
        boolean isLevelUp = checkAndApplyLevelUp(userId, oldLevel, newLevel);

        User updatedUser = userMapper.findById(userId);
        GrowthLevel growthLevel = growthLevelMapper.findByLevel(nullToDefault(updatedUser.getCurrentLevel(), 1));

        return new ExpLevelResult(expChange, updatedUser, growthLevel, isLevelUp);
    }

    private void applyExpIfPositive(Long userId, int expChange) {
        if (expChange > 0) {
            userMapper.addExp(userId, expChange);
        }
    }

    private boolean checkAndApplyLevelUp(Long userId, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel || newLevel > MAX_LEVEL) {
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

    private int calculateNewLevel(int currentExp) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (currentExp >= LEVEL_THRESHOLDS[level - 1]) {
                return level;
            }
        }
        return 1;
    }

    // =========================================================================
    // Response Building
    // =========================================================================

    private SavingsRecordResponse buildSavingsResponse(
            DreamHome dreamHome,
            long newSavedAmount,
            boolean isCompleted,
            ExpLevelResult expResult
    ) {
        DreamHome updatedDreamHome = DreamHome.builder()
                .dreamHomeId(dreamHome.getDreamHomeId())
                .currentSavedAmount(newSavedAmount)
                .targetAmount(dreamHome.getTargetAmount())
                .status(isCompleted ? DreamHomeStatus.COMPLETED : DreamHomeStatus.ACTIVE)
                .build();

        return SavingsRecordResponse.from(
                updatedDreamHome,
                expResult.expChange(),
                expResult.user(),
                expResult.growthLevel(),
                expResult.isLevelUp()
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
