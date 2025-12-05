package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저 컬렉션 (완성한 집) 도메인
 * - 목표 달성 후 컬렉션에 추가되는 집
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCollection {

    private Long collectionId;
    private Long userId;
    private Integer themeId;
    private String houseName;
    private LocalDateTime completedAt;
    private Boolean isMainDisplay;
    private Long totalSaved;
    private Integer durationDays;
}
