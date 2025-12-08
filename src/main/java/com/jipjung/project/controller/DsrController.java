package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.request.DsrSimulationRequest;
import com.jipjung.project.controller.dto.response.DsrSimulationResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.service.DsrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * DSR 시뮬레이션 컨트롤러
 * <p>
 * PRO 모드 DSR 시뮬레이션 API 제공.
 */
@Tag(name = "DSR 시뮬레이션", description = "DSR 상세 시뮬레이션 API")
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class DsrController {

    private final DsrService dsrService;

    @Operation(
            summary = "DSR 시뮬레이션 (PRO 모드)",
            description = """
                    2026년 정책 기반 상세 DSR 시뮬레이션을 수행합니다.
                    
                    **적용 정책:**
                    - 스트레스 금리: 수도권 3.0%p, 비수도권 0.75%p
                    - 청년 장래소득: 20-34세 구간별 인정
                    - 대출 유형별 스트레스 반영율: 변동 100%, 혼합 70%, 주기형 40%, 고정 0%
                    
                    **응답 정보:**
                    - 현재 DSR 및 등급 (SAFE/WARNING/RESTRICTED)
                    - 최대 대출 가능액
                    - 적용된 정책 상세 (스트레스 금리, 장래소득 배율)
                    - 맞춤 시뮬레이션 팁
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시뮬레이션 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (입력값 검증 실패)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음"
            )
    })
    @PostMapping("/dsr")
    public ResponseEntity<ApiResponse<DsrSimulationResponse>> simulate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DsrSimulationRequest request
    ) {
        DsrSimulationResponse response = dsrService.simulate(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }
}
