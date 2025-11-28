package com.jipjung.project.controller;

import com.jipjung.project.config.exception.ApiResponse;
import com.jipjung.project.controller.dto.request.LoginRequest;
import com.jipjung.project.controller.dto.request.SignupRequest;
import com.jipjung.project.controller.response.LoginResponse;
import com.jipjung.project.controller.response.SignupResponse;
import com.jipjung.project.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입, 로그인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "회원가입",
            description = "이메일, 닉네임, 비밀번호로 회원가입을 진행합니다.\n\n" +
                    "**비밀번호 조건**: 8자 이상, 영문 + 숫자 + 특수문자 포함"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = SignupResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력 값 검증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 이메일"
            )
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인합니다.\n\n" +
                    "**성공 시**: 응답 헤더의 `Authorization`에 JWT 토큰이 포함됩니다.\n\n" +
                    "**참고**: 이 API는 실제로 CustomJsonUsernamePasswordAuthenticationFilter가 처리합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 - Authorization 헤더에 JWT 토큰 포함",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호가 올바르지 않음"
            )
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        // 이 메서드는 실제로 호출되지 않습니다.
        // CustomJsonUsernamePasswordAuthenticationFilter가 가로채서 처리합니다.
        // Swagger 문서화를 위한 더미 메서드입니다.
        throw new IllegalStateException("This method should not be called. It's intercepted by the filter.");
    }
}
