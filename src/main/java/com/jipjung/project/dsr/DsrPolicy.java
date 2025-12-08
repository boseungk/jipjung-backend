package com.jipjung.project.dsr;

/**
 * DSR/스트레스 금리/청년 장래소득 정책 파라미터
 * <p>
 * 정책 값을 record로 분리하여 단일 책임 원칙(SRP) 준수.
 * 정책 변경 시 이 클래스만 수정하면 됨.
 *
 * @param bankDsrLimitRatio       1금융권 DSR 한도 (0.40 = 40%)
 * @param nonBankDsrLimitRatio    2금융권 DSR 한도 (0.50 = 50%)
 * @param seoulMetroStressBase    수도권 스트레스 금리 기본값 (%p)
 * @param nonMetroStressBase      비수도권 스트레스 금리 기본값 (%p)
 * @param youth20to24Multiplier   20-24세 장래소득 인정 배율
 * @param youth25to29Multiplier   25-29세 장래소득 인정 배율
 * @param youth30to34Multiplier   30-34세 장래소득 인정 배율
 * @param enableYouthFutureIncome 청년 장래소득 인정 활성화 여부
 */
public record DsrPolicy(
        double bankDsrLimitRatio,
        double nonBankDsrLimitRatio,
        double seoulMetroStressBase,
        double nonMetroStressBase,
        double youth20to24Multiplier,
        double youth25to29Multiplier,
        double youth30to34Multiplier,
        boolean enableYouthFutureIncome
) {

    // =========================================================================
    // Factory Methods - 정책 버전별 생성
    // =========================================================================

    /**
     * 2025년 12월 기준 1금융권 기본 정책
     * <p>
     * 금융위원회 DSR 규제 및 스트레스 금리 가이드라인 반영.
     *
     * @return 2025년 하반기 기준 정책 파라미터
     */
    public static DsrPolicy bankDefault2025H2() {
        return new DsrPolicy(
                0.40,    // 1금융권 DSR 한도 40%
                0.50,    // 2금융권 DSR 한도 50%
                3.0,     // 수도권 스트레스 금리 3.0%p
                0.75,    // 비수도권 스트레스 금리 0.75%p
                1.516,   // 20-24세 장래소득 +51.6%
                1.314,   // 25-29세 장래소득 +31.4%
                1.131,   // 30-34세 장래소득 +13.1%
                true     // 청년 장래소득 인정 활성화
        );
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * DSR 한도 비율 조회
     *
     * @param lenderType 금융기관 유형
     * @return DSR 한도 비율 (0.0 ~ 1.0)
     */
    public double getDsrLimitRatio(DsrInput.LenderType lenderType) {
        return switch (lenderType) {
            case BANK -> bankDsrLimitRatio;
            case NON_BANK -> nonBankDsrLimitRatio;
        };
    }

    /**
     * 지역별 기본 스트레스 금리 조회
     *
     * @param region 담보 물건 지역
     * @return 스트레스 금리 (%p)
     */
    public double getBaseStressRate(DsrInput.Region region) {
        return switch (region) {
            case SEOUL_METRO -> seoulMetroStressBase;
            case ETC -> nonMetroStressBase;
        };
    }

    /**
     * 나이별 장래소득 인정 배율 조회
     *
     * @param age 만 나이
     * @return 소득 인정 배율 (1.0 = 100%)
     */
    public double getYouthIncomeMultiplier(int age) {
        if (!enableYouthFutureIncome) {
            return 1.0;
        }

        if (age >= 20 && age <= 24) {
            return youth20to24Multiplier;
        } else if (age >= 25 && age <= 29) {
            return youth25to29Multiplier;
        } else if (age >= 30 && age <= 34) {
            return youth30to34Multiplier;
        }
        return 1.0;
    }
}
