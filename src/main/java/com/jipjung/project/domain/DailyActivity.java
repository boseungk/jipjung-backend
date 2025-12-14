package com.jipjung.project.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 활동 도메인
 * <p>
 * 사용자의 일일 활동을 기록하여 스트릭 및 EXP 시스템을 지원합니다.
 * 복합 유니크 제약(user_id, activity_date, activity_type)으로 하루에 같은 활동은 1회만 기록됩니다.
 * 
 * @see ActivityType
 * @see com.jipjung.project.service.StreakService
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyActivity {
    
    private Long activityId;
    private Long userId;
    private LocalDate activityDate;
    private String activityType;
    private Integer expEarned;
    private LocalDateTime createdAt;
    
    /**
     * 활동 유형 enum으로 변환
     * 
     * @return ActivityType enum, 유효하지 않은 값이면 null
     */
    public ActivityType getActivityTypeEnum() {
        if (activityType == null) {
            return null;
        }
        try {
            return ActivityType.valueOf(activityType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * 새 일일 활동 생성 (빌더 대안)
     * 
     * @param userId 사용자 ID
     * @param activityDate 활동 날짜 (KST)
     * @param type 활동 유형
     * @param expEarned 획득 EXP
     * @return 새 DailyActivity 인스턴스
     */
    public static DailyActivity of(Long userId, LocalDate activityDate, ActivityType type, int expEarned) {
        return DailyActivity.builder()
                .userId(userId)
                .activityDate(activityDate)
                .activityType(type.name())
                .expEarned(expEarned)
                .build();
    }
}
