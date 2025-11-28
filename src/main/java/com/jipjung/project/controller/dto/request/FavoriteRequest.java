package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관심 아파트 등록 요청")
public record FavoriteRequest(
        @Schema(description = "아파트 실거래가 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "아파트 실거래가 ID는 필수입니다")
        Long apartmentTransactionId
) {
}
