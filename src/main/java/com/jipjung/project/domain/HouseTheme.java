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

    /** 기본 CDN 베이스 URL */
    private static final String DEFAULT_CDN_BASE_URL = "https://storage.googleapis.com/jipjung-assets/";

    /** 실행 환경에서 CDN 베이스 URL을 오버라이드할 수 있는 환경변수 키 */
    private static final String CDN_BASE_URL_ENV_KEY = "JIPJUNG_CDN_BASE_URL";

    /** 기본 SVG 경로 (폴백용) */
    public static final String DEFAULT_IMAGE_PATH = "phase7.svg";

    private Integer themeId;
    private String themeCode;
    private String themeName;
    private String imagePath;  // 상대 경로 (예: themes/modern/phase.svg)
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
    
    /**
     * 전체 이미지 URL 반환
     * @return CDN 전체 URL 또는 폴백 경로
     */
    public String getFullImageUrl() {
        if (imagePath == null || imagePath.isBlank()) {
            return "/" + DEFAULT_IMAGE_PATH;
        }

        String normalizedPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
        return resolveCdnBaseUrl() + normalizedPath;
    }

    private static String resolveCdnBaseUrl() {
        String baseUrl = System.getenv(CDN_BASE_URL_ENV_KEY);
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_CDN_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
}
