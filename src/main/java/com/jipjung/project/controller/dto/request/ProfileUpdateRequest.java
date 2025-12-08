package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청")
public record ProfileUpdateRequest(
        @Schema(description = "닉네임 (2-20자)", example = "건축왕2세", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "닉네임은 필수입니다")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
        String nickname,

        @Schema(description = "연소득 (원 단위)", example = "60000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        @Schema(description = "월 기존 대출 상환액 (원 단위)", example = "400000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "월 기존 대출 상환액은 필수입니다")
        @Min(value = 0, message = "월 기존 대출 상환액은 0 이상이어야 합니다")
        Long existingLoanMonthly
) {}
