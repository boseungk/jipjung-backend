package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.request.OnboardingRequest;
import com.jipjung.project.controller.dto.request.ProfileUpdateRequest;
import com.jipjung.project.controller.dto.response.OnboardingResponse;
import com.jipjung.project.controller.dto.response.ProfileUpdateResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;
import com.jipjung.project.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 사용자 컨트롤러
 * - 온보딩 및 프로필 관리 API
 */
@Tag(name = "사용자", description = "온보딩 및 프로필 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "온보딩 정보 저장",
            description = """
                    사용자의 온보딩 정보를 저장합니다.
                    
                    **저장 정보:**
                    - 출생년도
                    - 연소득 (원 단위)
                    - 월 기존 대출 상환액 (원 단위)
                    - 선호 지역 (배열, 최대 10개, 각 50자 이내)
                    
                    **응답 정보:**
                    - 업데이트된 사용자 정보
                    - DSR 분석 결과 (SAFE/CAUTION/DANGER 3단계)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "온보딩 정보 저장 완료",
                    content = @Content(schema = @Schema(implementation = OnboardingResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요"
            )
    })
    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<OnboardingResponse>> saveOnboarding(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request
    ) {
        OnboardingResponse response = userService.saveOnboarding(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "프로필 수정",
            description = """
                    사용자 프로필 정보를 수정합니다.
                    
                    **수정 가능 필드:**
                    - 닉네임 (2-20자)
                    - 연소득 (원 단위)
                    - 월 기존 대출 상환액 (원 단위)
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = ProfileUpdateResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "유효성 검증 실패"
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
    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        ProfileUpdateResponse response = userService.updateProfile(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }
}
