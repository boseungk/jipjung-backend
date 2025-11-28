package com.jipjung.project.config.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.config.exception.ApiResponse;
import com.jipjung.project.config.jwt.JwtProvider;
import com.jipjung.project.controller.response.LoginResponse;
import com.jipjung.project.service.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * 로그인 성공 시 JWT 토큰을 생성하고 응답하는 핸들러
 */
@Slf4j
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getEmail();
        String nickname = userDetails.getNickname();

        // JWT 토큰 생성
        String accessToken = jwtProvider.createAccessToken(email);

        // 응답 설정
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Authorization", "Bearer " + accessToken);
        response.setStatus(HttpServletResponse.SC_OK);

        // 응답 body
        LoginResponse loginResponse = new LoginResponse(nickname);
        ApiResponse<LoginResponse> apiResponse = ApiResponse.success(loginResponse);

        String responseBody = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(responseBody);

        log.info("로그인 성공: {}", email);
    }
}
