package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.response.DashboardResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 컨트롤러
 * - 대시보드 통합 데이터 조회 API
 */
@Tag(name = "대시보드", description = "대시보드 통합 데이터 조회 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "대시보드 조회",
            description = """
                    대시보드에 표시되는 모든 정보를 통합 조회합니다.
                    
                    **포함 정보:**
                    - 프로필 (닉네임, 칭호, 레벨, 경험치)
                    - 목표 (목표 아파트, 저축 현황, 달성률)
                    - 스트릭 (연속 저축, 주간 현황)
                    - DSR (부채 상환 비율, 금융 정보)
                    - 자산 (총 자산, 성장률, 30일 차트)
                    - 쇼룸 (집짓기 시각화)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DashboardResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음"
            )
    })
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        DashboardResponse response = dashboardService.getDashboard(userDetails.getId());
        return ApiResponse.success(response);
    }
}
