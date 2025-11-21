package com.jipjung.project.config.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.config.exception.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * 로그인 실패 시 에러 응답을 반환하는 핸들러
 */
@Slf4j
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> apiResponse = ApiResponse.error(
                HttpServletResponse.SC_UNAUTHORIZED,
                "로그인 실패: 이메일 또는 비밀번호가 올바르지 않습니다."
        );

        String responseBody = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(responseBody);

        log.warn("로그인 실패: {}", exception.getMessage());
    }
}
