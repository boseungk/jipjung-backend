package com.jipjung.project.controller.dto.request;

import com.jipjung.project.domain.InputMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 지출 분석 요청 DTO
 * <p>
 * INPUT_MODE에 따라 검증 규칙이 다릅니다:
 * <ul>
 *   <li>MANUAL: amount, storeName, category, paymentDate 필수</li>
 *   <li>IMAGE: 이미지 파일 필수, 나머지 필드 무시</li>
 * </ul>
 * Bean Validation은 기본 형식 검증만 수행하고,
 * 모드별 필수 필드 검증은 서비스 레이어에서 처리합니다.
 */
@Schema(description = "지출 분석 요청")
public record SpendingAnalyzeRequest(

    @Schema(description = "입력 모드", example = "MANUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "입력 모드는 필수입니다")
    InputMode inputMode,

    @Schema(description = "금액 (원)", example = "31000")
    @Min(value = 1, message = "금액은 1원 이상이어야 합니다")
    Long amount,

    @Schema(description = "가게명", example = "치킨플러스")
    @Size(max = 100, message = "가게명은 100자 이하여야 합니다")
    String storeName,

    @Schema(description = "카테고리", example = "FOOD")
    @Pattern(regexp = "FOOD|TRANSPORT|SHOPPING|ENTERTAINMENT|LIVING|ETC",
             message = "유효하지 않은 카테고리입니다")
    String category,

    @Schema(description = "결제일", example = "2025-12-04")
    LocalDate paymentDate,

    @Schema(description = "메모 (선택)", example = "")
    @Size(max = 255, message = "메모는 255자 이하여야 합니다")
    String memo

) {}
