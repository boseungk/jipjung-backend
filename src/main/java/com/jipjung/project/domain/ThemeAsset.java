package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 테마별 레벨 이미지 에셋 도메인
 * - 각 테마 + 레벨 조합별 집 이미지 URL
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThemeAsset {

    public static final String DEFAULT_IMAGE_URL = "/assets/house/default.png";

    private Long assetId;
    private Integer themeId;
    private Integer level;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;

    /**
     * 기본 이미지 URL 반환용 팩토리
     */
    public static ThemeAsset defaultAsset() {
        return ThemeAsset.builder()
                .imageUrl(DEFAULT_IMAGE_URL)
                .build();
    }
}
