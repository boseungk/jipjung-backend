package com.jipjung.project.config.jwt.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.config.jwt.JwtProvider;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.LoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 토큰을 검증하고 인증 정보를 설정하는 필터
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTH_PATH = "/api/auth";

    private final JwtProvider jwtProvider;
    private final LoginService loginService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 인증 관련 URL은 필터를 건너뜀 (회원가입, 로그인 등)
        if (request.getRequestURI().startsWith(AUTH_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        String token = jwtProvider.extractToken(authorizationHeader);

        try {
            if (token != null) {
                String email = jwtProvider.getEmailFromToken(token);

                if (email == null || email.isBlank()) {
                    writeUnauthorized(response, ErrorCode.INVALID_AUTH_TOKEN);
                    return;
                }

                UserDetails userDetails = loginService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("인증 성공: {}", email);
            }

            filterChain.doFilter(request, response);
        } catch (TokenExpiredException e) {
            log.warn("만료된 토큰 요청: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, ErrorCode.EXPIRED_AUTH_TOKEN);
        } catch (JWTVerificationException | UsernameNotFoundException e) {
            log.warn("유효하지 않은 토큰 요청: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, ErrorCode.INVALID_AUTH_TOKEN);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(errorCode.getStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.errorBody(errorCode);
        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}
