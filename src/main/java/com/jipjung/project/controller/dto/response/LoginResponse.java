package com.jipjung.project.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(
        @Schema(description = "닉네임", example = "홍길동")
        String nickname
) {
}
