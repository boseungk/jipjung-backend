package com.jipjung.project.global.exception;

/**
 * 비즈니스 로직 예외
 * <p>
 * 비즈니스 규칙 위반 시 발생하는 예외 (400, 500 등)
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
