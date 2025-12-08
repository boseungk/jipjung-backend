package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 하우스 테마 도메인
 * - 집짓기 시각화에 사용되는 테마 (MODERN, HANOK, CASTLE 등)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseTheme {

    private Integer themeId;
    private String themeCode;
    private String themeName;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}
