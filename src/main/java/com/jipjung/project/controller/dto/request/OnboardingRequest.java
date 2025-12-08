package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.stream.Collectors;

@Schema(description = "온보딩 정보 저장 요청")
public record OnboardingRequest(
        @Schema(description = "출생년도", example = "1995", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "출생년도는 필수입니다")
        @Min(value = 1900, message = "출생년도는 1900 이상이어야 합니다")
        @Max(value = 2010, message = "출생년도는 2010 이하여야 합니다")
        Integer birthYear,

        @Schema(description = "연소득 (원 단위)", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        @Schema(description = "월 기존 대출 상환액 (원 단위)", example = "500000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "월 기존 대출 상환액은 필수입니다")
        @Min(value = 0, message = "월 기존 대출 상환액은 0 이상이어야 합니다")
        Long existingLoanMonthly,

        @Schema(description = "현재 보유 자산 (원 단위)", example = "30000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "현재 자산은 필수입니다")
        @Min(value = 0, message = "현재 자산은 0 이상이어야 합니다")
        Long currentAssets,

        @Schema(description = "선호 지역 배열 (각 항목 50자 이내, 최대 10개, 중복 불가)",
                example = "[\"강남구\", \"서초구\", \"송파구\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "선호 지역은 최소 1개 이상이어야 합니다")
        @Size(max = 10, message = "선호 지역은 최대 10개까지 선택 가능합니다")
        List<@NotBlank(message = "선호 지역은 공백일 수 없습니다")
                @Size(max = 50, message = "선호 지역은 50자 이하여야 합니다") String> preferredAreas
) {
    /**
     * preferredAreas를 정제하여 반환
     * - trim 처리
     * - 빈 문자열 제거
     * - 중복 제거
     * - 50자 초과 항목 제거
     */
    public List<String> getSanitizedPreferredAreas() {
        if (preferredAreas == null) {
            return List.of();
        }
        return preferredAreas.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> s.length() <= 50)
                .distinct()
                .collect(Collectors.toList());
    }
}
