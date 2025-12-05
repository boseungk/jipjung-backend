package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 드림홈(목표) 도메인
 * - 사용자의 저축 목표 아파트 및 금액 정보
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DreamHome {

    private Long dreamHomeId;
    private Long userId;
    private String aptSeq;
    private Long targetAmount;
    private LocalDate targetDate;
    private Long monthlyGoal;
    private Long currentSavedAmount;
    private LocalDate startDate;
    private DreamHomeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;

    // 조인된 아파트 정보 (optional)
    private Apartment apartment;

    /**
     * 목표 달성 여부
     */
    public boolean isCompleted() {
        return getSafeCurrentSavedAmount() >= getSafeTargetAmount();
    }

    /**
     * 남은 금액 계산 (음수 방지)
     */
    public long getRemainingAmount() {
        return Math.max(0, getSafeTargetAmount() - getSafeCurrentSavedAmount());
    }

    /**
     * 달성률 계산 (0~100)
     */
    public double getAchievementRate() {
        long target = getSafeTargetAmount();
        if (target == 0) {
            return 0.0;
        }
        double rate = (getSafeCurrentSavedAmount() * 100.0) / target;
        return Math.round(rate * 10.0) / 10.0;  // 소수점 1자리
    }

    private long getSafeTargetAmount() {
        return targetAmount != null ? targetAmount : 0L;
    }

    private long getSafeCurrentSavedAmount() {
        return currentSavedAmount != null ? currentSavedAmount : 0L;
    }
}
