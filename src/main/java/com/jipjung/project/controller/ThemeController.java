package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.response.HouseThemeResponse;
import com.jipjung.project.domain.HouseTheme;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.repository.HouseThemeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 테마 관리 컨트롤러
 * <p>
 * 하우스 테마 조회 API를 제공합니다.
 * 드림홈 설정 모달에서 테마 선택 UI에 사용됩니다.
 *
 * @see HouseTheme
 */
@Tag(name = "테마", description = "하우스 테마 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final HouseThemeMapper houseThemeMapper;

    /**
     * 활성 테마 목록 조회
     * <p>
     * 사용자가 드림홈 설정 시 선택할 수 있는 테마 목록을 반환합니다.
     * 비활성화되거나 삭제된 테마는 제외됩니다.
     *
     * @return 활성 테마 목록
     */
    @Operation(
            summary = "활성 테마 목록 조회",
            description = """
                    드림홈 설정 시 선택할 수 있는 테마 목록을 조회합니다.
                    
                    **반환 정보:**
                    - themeId: 테마 고유 ID
                    - themeCode: 테마 코드 (MODERN, HANOK, CASTLE 등)
                    - themeName: 테마 표시명 (모던 하우스, 한옥, 서양 성 등)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "테마 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = HouseThemeResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요"
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<HouseThemeResponse>>> getActiveThemes() {
        List<HouseTheme> themes = houseThemeMapper.findAllActive();
        log.debug("조회된 활성 테마 수: {}", themes.size());
        List<HouseThemeResponse> response = themes.stream()
                .map(HouseThemeResponse::from)
                .toList();
        return ApiResponse.success(response);
    }
}
