package com.jipjung.project.controller;

import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.controller.dto.request.DreamHomeSetRequest;
import com.jipjung.project.controller.dto.request.SavingsRecordRequest;
import com.jipjung.project.controller.dto.response.DreamHomeSetResponse;
import com.jipjung.project.controller.dto.response.SavingsRecordResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.DreamHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 드림홈(목표) 관리 컨트롤러
 */
@Tag(name = "드림홈", description = "드림홈(목표) 관리 API")
@RestController
@RequestMapping("/api/dream-home")
@RequiredArgsConstructor
public class DreamHomeController {

    private final DreamHomeService dreamHomeService;

    @Operation(
            summary = "드림홈 설정",
            description = """
                    저축 목표 아파트를 설정합니다.

                    **규칙:**
                    - 한 번에 하나의 활성 드림홈만 가질 수 있습니다
                    - 기존 활성 드림홈이 있으면 새 목표로 업데이트됩니다 (저축액/기록 유지)
                    - 목표 달성일은 미래 날짜여야 합니다
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "드림홈 설정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<DreamHomeSetResponse>> setDreamHome(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DreamHomeSetRequest request
    ) {
        DreamHomeSetResponse response = dreamHomeService.setDreamHome(userDetails.getId(), request);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "저축 기록",
            description = """
                    저축(입금/출금)을 기록합니다.

                    **경험치 규칙:**
                    - DEPOSIT: 1만원당 1 EXP 획득 (최대 500 EXP/건)
                    - WITHDRAW: 경험치 획득 없음 (0 EXP)

                    **레벨업:**
                    - 경험치가 현재 레벨의 requiredExp를 초과하면 레벨업
                    - 최대 레벨 7 (완공)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저축 기록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "활성 드림홈이 없음")
    })
    @PostMapping("/savings")
    public ResponseEntity<ApiResponse<SavingsRecordResponse>> recordSavings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SavingsRecordRequest request
    ) {
        SavingsRecordResponse response = dreamHomeService.recordSavings(userDetails.getId(), request);
        return ApiResponse.success(response);
    }
}
