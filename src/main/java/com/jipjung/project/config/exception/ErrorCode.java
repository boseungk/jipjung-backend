package com.jipjung.project.config.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(400, "잘못된 입력 값입니다"),
    INVALID_TYPE_VALUE(400, "잘못된 타입입니다"),

    // 401 Unauthorized
    UNAUTHORIZED(401, "인증이 필요합니다"),
    INVALID_AUTH_TOKEN(401, "유효하지 않은 인증 토큰입니다"),
    EXPIRED_AUTH_TOKEN(401, "만료된 인증 토큰입니다"),

    // 403 Forbidden
    FORBIDDEN(403, "권한이 없습니다"),

    // 404 Not Found
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),

    // 409 Conflict
    DUPLICATE_EMAIL(409, "이미 존재하는 이메일입니다"),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다");

    private final int status;
    private final String message;
}
