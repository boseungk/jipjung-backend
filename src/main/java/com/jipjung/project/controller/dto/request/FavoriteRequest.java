package com.jipjung.project.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 관심 아파트 등록 요청 DTO
 * schema.sql의 favorite_apartment 테이블 기준
 */
public record FavoriteRequest(
        @NotBlank(message = "아파트 코드는 필수입니다")
        String aptSeq  // 아파트 코드 (apartment.apt_seq)
) {
}
