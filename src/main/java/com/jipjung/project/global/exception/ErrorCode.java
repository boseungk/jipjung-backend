package com.jipjung.project.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(400, "잘못된 입력 값입니다"),
    INVALID_TYPE_VALUE(400, "잘못된 타입입니다"),
    INVALID_PASSWORD(400, "비밀번호가 일치하지 않습니다"),
    AI_CONVERSATION_INVALID_STATUS(400, "잘못된 대화 상태입니다"),

    // 401 Unauthorized
    UNAUTHORIZED(401, "인증이 필요합니다"),
    INVALID_AUTH_TOKEN(401, "유효하지 않은 인증 토큰입니다"),
    EXPIRED_AUTH_TOKEN(401, "만료된 인증 토큰입니다"),

    // 403 Forbidden
    FORBIDDEN(403, "권한이 없습니다"),

    // 404 Not Found
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    APARTMENT_NOT_FOUND(404, "아파트를 찾을 수 없습니다"),
    FAVORITE_NOT_FOUND(404, "관심 아파트를 찾을 수 없습니다"),
    DREAM_HOME_NOT_FOUND(404, "활성 드림홈이 없습니다"),
    AI_CONVERSATION_NOT_FOUND(404, "AI 대화를 찾을 수 없습니다"),
    THEME_NOT_FOUND(404, "테마를 찾을 수 없습니다"),
    COLLECTION_NOT_FOUND(404, "컬렉션을 찾을 수 없습니다"),
    COLLECTION_ACCESS_DENIED(403, "컬렉션에 대한 접근 권한이 없습니다"),
    COLLECTION_JOURNEY_NOT_AVAILABLE(400, "여정 정보를 사용할 수 없습니다"),

    // 400 Bad Request - Theme
    THEME_NOT_ACTIVE(400, "비활성화된 테마입니다"),

    // 409 Conflict
    DUPLICATE_EMAIL(409, "이미 존재하는 이메일입니다"),
    DUPLICATE_FAVORITE(409, "이미 관심 아파트로 등록되어 있습니다"),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다"),
    AI_SERVICE_ERROR(500, "AI 서비스 오류가 발생했습니다"),

    // Streak 관련 에러
    STREAK_ALREADY_PARTICIPATED(400, "오늘 이미 스트릭에 참여했습니다"),
    STREAK_REWARD_NOT_ELIGIBLE(400, "보상 수령 조건을 충족하지 않습니다"),
    STREAK_REWARD_ALREADY_CLAIMED(400, "이미 수령한 마일스톤 보상입니다");

    private final int status;
    private final String message;
}
