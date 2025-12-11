package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 추출 데이터 확인 요청 DTO
 * <p>
 * IMAGE 모드로 영수증을 분석한 후, 추출된 데이터를 사용자가 확인/수정하여 제출합니다.
 * 모든 필드가 필수이며, 누락 시 400 에러를 반환합니다.
 */
@Schema(description = "추출 데이터 확인 요청")
public record ConfirmExtractedDataRequest(

    @Schema(description = "대화 ID", example = "502", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "대화 ID는 필수입니다")
    Long conversationId,

    @Schema(description = "금액 (원)", example = "31000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "금액은 필수입니다")
    @Min(value = 1, message = "금액은 1원 이상이어야 합니다")
    Long amount,

    @Schema(description = "가게명", example = "치킨플러스 강남점", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "가게명은 필수입니다")
    @Size(max = 100, message = "가게명은 100자 이하여야 합니다")
    String storeName,

    @Schema(description = "카테고리", example = "FOOD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "카테고리는 필수입니다")
    @Pattern(regexp = "FOOD|TRANSPORT|SHOPPING|ENTERTAINMENT|LIVING|ETC",
             message = "유효하지 않은 카테고리입니다")
    String category,

    @Schema(description = "결제일", example = "2025-12-10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "결제일은 필수입니다")
    LocalDate paymentDate,

    @Schema(description = "메모 (선택)", example = "")
    @Size(max = 255, message = "메모는 255자 이하여야 합니다")
    String memo

) {}
