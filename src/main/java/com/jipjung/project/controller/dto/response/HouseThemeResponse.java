package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.HouseTheme;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 하우스 테마 응답 DTO
 * - 테마 선택 UI에서 사용되는 최소 정보만 노출
 */
@Schema(description = "하우스 테마 응답")
public record HouseThemeResponse(
        @Schema(description = "테마 ID") Integer themeId,
        @Schema(description = "테마 코드") String themeCode,
        @Schema(description = "테마 이름") String themeName,
        @Schema(description = "테마 미리보기 이미지 URL") String previewImageUrl
) {
    public static HouseThemeResponse from(HouseTheme theme) {
        if (theme == null) {
            return null;
        }
        return new HouseThemeResponse(
                theme.getThemeId(),
                theme.getThemeCode(),
                theme.getThemeName(),
                theme.getFullImageUrl()
        );
    }
}
