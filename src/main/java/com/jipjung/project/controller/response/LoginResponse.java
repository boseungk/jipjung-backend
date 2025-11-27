package com.jipjung.project.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "로그인 응답")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    @Schema(description = "닉네임", example = "홍길동")
    private String nickname;
}
