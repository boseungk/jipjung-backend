package com.jipjung.project.config.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int code;
    private final String status;
    private final String message;
    private final T data;

    private ApiResponse(int code, String status, String message, T data) {
        this.code = code;
        this.status = status;
        this.message = message;
        this.data = data;
    }

    /**
     * 성공 응답 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                HttpStatus.OK.name(),
                "성공",
                data
        );
    }

    /**
     * 성공 응답 (데이터 없음)
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                HttpStatus.OK.name(),
                "성공",
                null
        );
    }

    /**
     * 에러 응답
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(
                code,
                HttpStatus.valueOf(code).name(),
                message,
                null
        );
    }

    /**
     * 에러 응답 (ErrorCode 사용)
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                errorCode.getStatus(),
                HttpStatus.valueOf(errorCode.getStatus()).name(),
                errorCode.getMessage(),
                null
        );
    }

    /**
     * 에러 응답 (데이터 포함)
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(
                code,
                HttpStatus.valueOf(code).name(),
                message,
                data
        );
    }
}
