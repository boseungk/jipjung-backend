package com.jipjung.project.dsr;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DSR 시뮬레이션 결과
 * <p>
 * 불변 record로 결과 데이터 캡슐화.
 * 등급 계산은 DsrCalculator에서 수행하고 결과만 보관.
 *
 * @param currentDsrPercent       현재 DSR (%)
 * @param dsrAfterMaxLoanPercent  최대 한도 대출 시 DSR (%)
 * @param grade                   등급 (SAFE/WARNING/RESTRICTED)
 * @param maxLoanAmount           최대 대출 가능액 (원)
 */
@Schema(description = "DSR 시뮬레이션 결과")
public record DsrResult(

        @Schema(description = "현재 DSR (%)", example = "15.5")
        double currentDsrPercent,

        @Schema(description = "최대 한도 대출 시 DSR (%)", example = "39.8")
        double dsrAfterMaxLoanPercent,

        @Schema(description = "등급 (SAFE/WARNING/RESTRICTED)", example = "SAFE")
        String grade,

        @Schema(description = "최대 대출 가능액 (원)", example = "420000000")
        long maxLoanAmount
) {

    // =========================================================================
    // 등급 상수 (DsrCalculator에서 사용)
    // =========================================================================

    /** 안전: DSR 한도 5%p 이상 여유 */
    public static final String GRADE_SAFE = "SAFE";

    /** 주의: DSR 한도 5%p 이내 */
    public static final String GRADE_WARNING = "WARNING";

    /** 제한: DSR 한도 도달/초과 */
    public static final String GRADE_RESTRICTED = "RESTRICTED";

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * 추가 대출 불가 상태 생성
     *
     * @param currentDsrPercent 현재 DSR
     * @return RESTRICTED 등급의 결과
     */
    public static DsrResult restricted(double currentDsrPercent) {
        return new DsrResult(currentDsrPercent, currentDsrPercent, GRADE_RESTRICTED, 0L);
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * 추가 대출 가능 여부
     *
     * @return true if 추가 대출 가능
     */
    public boolean canBorrowMore() {
        return maxLoanAmount > 0 && !GRADE_RESTRICTED.equals(grade);
    }

    /**
     * 안전 등급 여부
     *
     * @return true if SAFE 등급
     */
    public boolean isSafe() {
        return GRADE_SAFE.equals(grade);
    }
}
