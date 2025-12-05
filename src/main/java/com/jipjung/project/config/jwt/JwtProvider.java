package com.jipjung.project.config.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    private static final String ACCESS_TOKEN_SUBJECT = "AccessToken";
    private static final String EMAIL_CLAIM = "email";
    private static final String BEARER = "Bearer ";

    /**
     * JWT Access Token 생성
     */
    public String createAccessToken(String email) {
        Date now = new Date();
        return JWT.create()
                .withSubject(ACCESS_TOKEN_SUBJECT)
                .withExpiresAt(new Date(now.getTime() + accessTokenExpiration))
                .withClaim(EMAIL_CLAIM, email)
                .sign(Algorithm.HMAC512(secret));
    }

    /**
     * JWT 토큰에서 이메일 추출
     */
    public String getEmailFromToken(String token) {
        DecodedJWT decodedJWT = verifyToken(token);
        return decodedJWT.getClaim(EMAIL_CLAIM).asString();
    }

    /**
     * JWT 토큰 유효성 검증
     */
    public DecodedJWT verifyToken(String token) throws JWTVerificationException {
        return JWT.require(Algorithm.HMAC512(secret))
                .build()
                .verify(token);
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    public String extractToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String value = authorizationHeader.trim();
        if (value.isEmpty()) {
            return null;
        }

        // Allow mixed-case "bearer" and gracefully handle double-prefix cases (e.g. "Bearer Bearer <token>")
        String lowerValue = value.toLowerCase();
        if (lowerValue.startsWith(BEARER.toLowerCase())) {
            value = value.substring(BEARER.length()).trim();
        }
        lowerValue = value.toLowerCase();
        if (lowerValue.startsWith(BEARER.toLowerCase())) {
            value = value.substring(BEARER.length()).trim();
        }

        return value.isEmpty() ? null : value;
    }
}
