package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 스트릭 마일스톤 보상 도메인
 * <p>
 * 사용자가 연속 활동 마일스톤(7/14/21/28일)을 달성하여 수령한 보상 기록.
 * 각 마일스톤은 사용자당 1회만 수령 가능.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreakMilestoneReward {

    private Long rewardId;
    private Long userId;
    
    /** 마일스톤 일수 (7, 14, 21, 28) */
    private Integer milestoneDays;
    
    /** 지급된 경험치 */
    private Integer expReward;
    
    /** 수령 시각 */
    private LocalDateTime claimedAt;
    
    /** 수령 시점의 연속일수 (감사/분석용) */
    private Integer streakCountAtClaim;
}
