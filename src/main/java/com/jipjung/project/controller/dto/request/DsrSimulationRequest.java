package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * DSR 시뮬레이션 요청 DTO (PRO 모드)
 * <p>
 * Bean Validation으로 입력값 검증 (Fail Fast).
 * 잘못된 enum 값은 @Pattern으로 400 에러 반환.
 */
@Schema(description = "DSR 시뮬레이션 요청 (PRO 모드)")
public record DsrSimulationRequest(

        @Schema(description = "연소득 (원)", example = "60000000")
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        @Schema(description = "지역 (SEOUL_METRO/ETC)", example = "SEOUL_METRO")
        @NotBlank(message = "지역은 필수입니다")
        @Pattern(regexp = "SEOUL_METRO|ETC", message = "지역은 SEOUL_METRO 또는 ETC만 가능합니다")
        String region,

        @Schema(description = "기존 연간 원리금 상환액 (원)", example = "3000000")
        @NotNull(message = "기존 연간 원리금 상환액은 필수입니다")
        @Min(value = 0, message = "기존 연간 원리금 상환액은 0 이상이어야 합니다")
        Long existingAnnualDebtService,

        @Schema(description = "전세대출 잔액 (원, 선택)", example = "200000000")
        @Min(value = 0, message = "전세대출 잔액은 0 이상이어야 합니다")
        Long jeonseLoanBalance,

        @Schema(description = "전세대출 금리 (%, 선택)", example = "4.0")
        @DecimalMin(value = "0.0", message = "전세대출 금리는 0 이상이어야 합니다")
        @DecimalMax(value = "30.0", message = "전세대출 금리는 30 이하여야 합니다")
        Double jeonseLoanRate,

        @Schema(description = "전세대출 DSR 포함 여부 (선택)", example = "true")
        Boolean jeonseIncludedInDsr,

        @Schema(description = "대출 유형 (VARIABLE/MIXED/PERIODIC/FIXED)", example = "PERIODIC")
        @NotBlank(message = "대출 유형은 필수입니다")
        @Pattern(regexp = "VARIABLE|MIXED|PERIODIC|FIXED",
                message = "대출 유형은 VARIABLE/MIXED/PERIODIC/FIXED 중 하나여야 합니다")
        String targetLoanType,

        @Schema(description = "예상 대출 금리 (%)", example = "4.0")
        @NotNull(message = "예상 대출 금리는 필수입니다")
        @DecimalMin(value = "0.0", message = "예상 대출 금리는 0 이상이어야 합니다")
        @DecimalMax(value = "30.0", message = "예상 대출 금리는 30 이하여야 합니다")
        Double targetLoanRate,

        @Schema(description = "대출 만기 (년)", example = "40")
        @NotNull(message = "대출 만기는 필수입니다")
        @Min(value = 1, message = "대출 만기는 1년 이상이어야 합니다")
        @Max(value = 50, message = "대출 만기는 50년 이하여야 합니다")
        Integer maturityYears,

        @Schema(description = "금융기관 유형 (BANK/NON_BANK, 기본: BANK)", example = "BANK")
        @Pattern(regexp = "BANK|NON_BANK", message = "금융기관 유형은 BANK 또는 NON_BANK만 가능합니다")
        String lenderType
) {

    // =========================================================================
    // Default Value Helpers
    // =========================================================================

    /**
     * 전세대출 잔액 (null-safe)
     */
    public long jeonseLoanBalanceOrZero() {
        return jeonseLoanBalance != null ? jeonseLoanBalance : 0L;
    }

    /**
     * 전세대출 금리 (null-safe)
     */
    public double jeonseLoanRateOrZero() {
        return jeonseLoanRate != null ? jeonseLoanRate : 0.0;
    }

    /**
     * 전세대출 DSR 포함 여부 (null-safe)
     */
    public boolean isJeonseIncludedInDsr() {
        return jeonseIncludedInDsr != null && jeonseIncludedInDsr;
    }

    /**
     * 금융기관 유형 (기본값: BANK)
     */
    public String lenderTypeOrDefault() {
        return lenderType != null ? lenderType : "BANK";
    }

    /**
     * 전세대출 잔액/금리 교차 검증
     * - 둘 다 0 또는 둘 다 양수여야 함
     */
    @AssertTrue(message = "전세대출 잔액과 금리는 함께 입력해야 합니다 (둘 다 0이거나 둘 다 양수)")
    public boolean isJeonseLoanValid() {
        boolean hasBalance = jeonseLoanBalance != null && jeonseLoanBalance > 0;
        boolean hasRate = jeonseLoanRate != null && jeonseLoanRate > 0.0;
        return (!hasBalance && !hasRate) || (hasBalance && hasRate);
    }
}
