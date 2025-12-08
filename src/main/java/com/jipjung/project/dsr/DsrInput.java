package com.jipjung.project.dsr;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DSR 시뮬레이션 입력 파라미터
 * <p>
 * 불변 record로 설계하여 스레드 안전성 보장.
 * enum 타입으로 유효한 값만 허용 (컴파일 타임 검증).
 */
@Schema(description = "DSR 시뮬레이션 입력")
public record DsrInput(

        @Schema(description = "연간 소득 (원)", example = "50000000")
        long annualIncome,

        @Schema(description = "차주 나이 (만 나이)", example = "32")
        int age,

        @Schema(description = "담보 물건지", example = "SEOUL_METRO")
        Region region,

        @Schema(description = "기존 대출 연간 원리금 상환액", example = "10000000")
        long existingAnnualDebtService,

        @Schema(description = "전세대출 잔액", example = "200000000")
        long jeonseLoanBalance,

        @Schema(description = "전세대출 금리 (%)", example = "4.0")
        double jeonseLoanRate,

        @Schema(description = "신규 대출 금리 유형", example = "PERIODIC")
        LoanType targetLoanType,

        @Schema(description = "신규 대출 예상 금리 (%)", example = "4.0")
        double targetLoanRate,

        @Schema(description = "신규 대출 만기 (년)", example = "30")
        int maturityYears,

        @Schema(description = "대출 기관 유형", example = "BANK")
        LenderType lenderType,

        @Schema(description = "전세대출 DSR 포함 여부", example = "true")
        boolean jeonseIncludedInDsr
) {

    /**
     * 담보 물건 지역
     */
    public enum Region {
        /** 수도권 (서울, 경기, 인천) - 스트레스 금리 3.0%p */
        SEOUL_METRO,
        /** 비수도권 - 스트레스 금리 0.75%p */
        ETC
    }

    /**
     * 대출 금리 유형
     * <p>
     * 스트레스 금리 적용 비율이 다름:
     * - VARIABLE: 100%
     * - MIXED: 70%
     * - PERIODIC: 40%
     * - FIXED: 0%
     */
    public enum LoanType {
        /** 변동금리 - 스트레스 100% */
        VARIABLE,
        /** 혼합형 (초기 고정 후 변동) - 스트레스 70% */
        MIXED,
        /** 주기형 (5년 단위 금리 조정) - 스트레스 40% */
        PERIODIC,
        /** 고정금리 - 스트레스 0% */
        FIXED
    }

    /**
     * 금융 기관 유형
     */
    public enum LenderType {
        /** 1금융권 (은행) - DSR 한도 40% */
        BANK,
        /** 2금융권 (저축은행, 캐피탈 등) - DSR 한도 50% */
        NON_BANK
    }
}
