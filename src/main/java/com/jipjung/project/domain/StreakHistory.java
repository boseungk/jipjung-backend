package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 스트릭(연속 저축) 기록 도메인
 * - 일별 저축 참여 기록 및 획득 경험치
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreakHistory {

    private Long streakId;
    private Long userId;
    private LocalDate streakDate;
    private Integer expEarned;
    private LocalDateTime createdAt;
}
