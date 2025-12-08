package com.jipjung.project.dsr;

import org.springframework.stereotype.Component;

/**
 * DSR 계산 핵심 로직
 * <p>
 * 정책(DsrPolicy)을 기반으로 DSR 비율과 최대 대출 가능액을 계산.
 * Stateless @Component로 설계하여 테스트 용이성 확보.
 *
 * <h3>계산 플로우</h3>
 * <ol>
 *   <li>청년 장래소득 인정</li>
 *   <li>기존 부채 + 전세 이자 합산</li>
 *   <li>DSR 여유 한도 계산</li>
 *   <li>스트레스 금리 반영</li>
 *   <li>최대 대출 원금 역산 (원리금 균등상환)</li>
 *   <li>등급 결정</li>
 * </ol>
 */
@Component
public class DsrCalculator {

    private static final int MONTHS_PER_YEAR = 12;

    /**
     * DSR 시뮬레이션 실행 및 최대 대출 가능금액 산출
     *
     * @param input  시뮬레이션 입력 (소득, 나이, 지역 등)
     * @param policy 적용 정책 (한도, 스트레스 금리 등)
     * @return 시뮬레이션 결과 (DSR %, 등급, 최대 대출액)
     */
    public DsrResult calculateMaxLoan(DsrInput input, DsrPolicy policy) {

        // 1. 소득 산정: 청년층 장래소득 인정
        long recognizedIncome = calculateRecognizedIncome(input.annualIncome(), input.age(), policy);

        if (recognizedIncome <= 0) {
            return DsrResult.restricted(0.0);
        }

        // 2. 기존 부채 + 전세대출 이자 반영
        long jeonseInterest = calculateJeonseInterest(input);
        long totalExistingDebtService = input.existingAnnualDebtService() + jeonseInterest;

        // 3. DSR 한도(금액 기준) → 신규 대출에 쓸 수 있는 여유 한도
        double dsrLimitRatio = policy.getDsrLimitRatio(input.lenderType());
        long maxAllowedTotalDebtService = (long) Math.floor(recognizedIncome * dsrLimitRatio);
        long availableForNewLoanService = Math.max(0L, maxAllowedTotalDebtService - totalExistingDebtService);

        if (availableForNewLoanService <= 0L) {
            double currentDsr = roundToOneDecimal(100.0 * totalExistingDebtService / recognizedIncome);
            return DsrResult.restricted(currentDsr);
        }

        // 4. 스트레스 금리 산출 (지역 + 상품 유형)
        double stressRateToAdd = calculateStressRate(input.region(), input.targetLoanType(), policy);
        double finalStressRate = input.targetLoanRate() + stressRateToAdd;

        // 5. 스트레스 금리로 최대 대출 가능 원금 역산 (원리금 균등)
        long maxLoanPrincipal = calculatePrincipalFromAnnualPayment(
                availableForNewLoanService, finalStressRate, input.maturityYears()
        );

        if (maxLoanPrincipal <= 0L) {
            double currentDsr = roundToOneDecimal(100.0 * totalExistingDebtService / recognizedIncome);
            return DsrResult.restricted(currentDsr);
        }

        // 6. DSR 계산 (현재 / 최대 대출 시)
        double currentDsr = 100.0 * totalExistingDebtService / recognizedIncome;

        // 스트레스 금리 기준 연간 상환액으로 규제 헤드룸을 평가
        long stressedAnnualDebtService = calculateAnnualDebtService(
                maxLoanPrincipal, finalStressRate, input.maturityYears()
        );

        double dsrAfterMaxLoan = 100.0 * (totalExistingDebtService + stressedAnnualDebtService) / recognizedIncome;

        // 7. 등급 결정
        String grade = determineGrade(dsrAfterMaxLoan, dsrLimitRatio);

        return new DsrResult(
                roundToOneDecimal(currentDsr),
                roundToOneDecimal(dsrAfterMaxLoan),
                grade,
                maxLoanPrincipal
        );
    }

    // =========================================================================
    // 스트레스 금리 계산 (외부 공개용)
    // =========================================================================

    /**
     * 적용될 스트레스 금리 계산
     * <p>
     * DsrService에서 응답에 포함시키기 위해 public으로 공개.
     *
     * @param region   담보 물건 지역
     * @param loanType 대출 금리 유형
     * @param policy   적용 정책
     * @return 스트레스 가산 금리 (%p)
     */
    public double calculateStressRate(DsrInput.Region region, DsrInput.LoanType loanType, DsrPolicy policy) {
        double base = policy.getBaseStressRate(region);
        double factor = getStressFactor(loanType);
        return roundToOneDecimal(base * factor);
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    /**
     * 청년 장래소득 인정 반영
     */
    private long calculateRecognizedIncome(long income, int age, DsrPolicy policy) {
        double multiplier = policy.getYouthIncomeMultiplier(age);
        return Math.round(income * multiplier);
    }

    /**
     * 전세대출 연간 이자 계산 (이자만 상환 가정)
     */
    private long calculateJeonseInterest(DsrInput input) {
        if (!input.jeonseIncludedInDsr() || input.jeonseLoanBalance() <= 0 || input.jeonseLoanRate() <= 0.0) {
            return 0L;
        }
        return Math.round(input.jeonseLoanBalance() * (input.jeonseLoanRate() / 100.0));
    }

    /**
     * 대출 유형별 스트레스 금리 적용 비율
     */
    private double getStressFactor(DsrInput.LoanType loanType) {
        return switch (loanType) {
            case VARIABLE -> 1.0;   // 100% 적용
            case MIXED -> 0.7;      // 70% 적용
            case PERIODIC -> 0.4;   // 40% 적용
            case FIXED -> 0.0;      // 미적용
        };
    }

    /**
     * 연간 상환액으로부터 대출 원금 역산 (원리금 균등상환)
     * <p>
     * PV = PMT × [(1 - (1 + r)^-n) / r]
     */
    private long calculatePrincipalFromAnnualPayment(long annualPayment, double annualRatePercent, int years) {
        double monthlyRate = (annualRatePercent / 100.0) / MONTHS_PER_YEAR;
        int totalMonths = years * MONTHS_PER_YEAR;
        double monthlyPayment = annualPayment / (double) MONTHS_PER_YEAR;

        if (monthlyRate <= 0.0) {
            // 0% 금리: 단순 곱셈
            return (long) Math.floor(monthlyPayment * totalMonths);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        return (long) Math.floor(monthlyPayment * pvFactor);
    }

    /**
     * 대출 원금으로부터 연간 상환액 계산 (원리금 균등상환)
     * <p>
     * PMT = PV / [(1 - (1 + r)^-n) / r]
     */
    private long calculateAnnualDebtService(long principal, double annualRatePercent, int years) {
        double monthlyRate = (annualRatePercent / 100.0) / MONTHS_PER_YEAR;
        int totalMonths = years * MONTHS_PER_YEAR;

        if (totalMonths <= 0) {
            return 0L;
        }

        if (monthlyRate <= 0.0) {
            // 0% 금리: 단순 나눗셈
            return Math.round((principal / (double) totalMonths) * MONTHS_PER_YEAR);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        double monthlyPayment = principal / pvFactor;

        return Math.round(monthlyPayment * MONTHS_PER_YEAR);
    }

    /**
     * 등급 결정 로직
     * <p>
     * - RESTRICTED: DSR 한도 도달/초과
     * - WARNING: 한도 5%p 이내
     * - SAFE: 여유 있음
     */
    private String determineGrade(double dsrAfterMaxLoan, double dsrLimitRatio) {
        double dsrLimitPercent = dsrLimitRatio * 100.0;

        if (dsrAfterMaxLoan >= dsrLimitPercent) {
            return DsrResult.GRADE_RESTRICTED;
        } else if (dsrAfterMaxLoan >= dsrLimitPercent - 5.0) {
            return DsrResult.GRADE_WARNING;
        }
        return DsrResult.GRADE_SAFE;
    }

    /**
     * 소수점 첫째 자리 반올림
     */
    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
