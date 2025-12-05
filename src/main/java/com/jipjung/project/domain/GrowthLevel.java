package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 성장 레벨 규칙 도메인
 * - 레벨별 단계명, 설명, 필요 경험치 정의
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthLevel {

    private Integer level;
    private String stepName;
    private String description;
    private Integer requiredExp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;

    /**
     * 레벨에 해당하는 건축가 칭호 생성
     */
    public String getTitle() {
        return stepName + " 건축가";
    }
}
