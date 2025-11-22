package com.jipjung.project.config.jwt.filter;

import com.jipjung.project.config.jwt.JwtProvider;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 토큰을 검증하고 인증 정보를 설정하는 필터
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String LOGIN_URL = "/api/auth/login";

    private final JwtProvider jwtProvider;
    private final LoginService loginService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 로그인 URL은 필터를 건너뜀
        if (request.getRequestURI().equals(LOGIN_URL)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더에서 토큰 추출
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        String token = jwtProvider.extractToken(authorizationHeader);

        // 토큰 검증 및 인증 정보 설정
        if (token != null && jwtProvider.validateToken(token)) {
            String email = jwtProvider.getEmailFromToken(token);

            if (email != null) {
                UserDetails userDetails = loginService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("인증 성공: {}", email);
            }
        }

        filterChain.doFilter(request, response);
    }
}
