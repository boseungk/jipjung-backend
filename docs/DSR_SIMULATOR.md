## 1. 정책 값 요약 (코드에 들어간 수치 근거)

실제 정책/보도자료 기준으로 최대한 맞춰서 박아둔 값들:

1. **DSR 한도**
    - 1금융(은행권) : **40%**
    - 제2금융권 : **50%**

      → Toss, 금융위 설명 등에서 “총 대출 1억 초과 시 은행 40%, 비은행 50%”로 명시. [토스](https://toss.im/tossfeed/article/ltv-dti-dsr?utm_source=chatgpt.com)

2. **스트레스 DSR**
    - 3단계 스트레스 DSR(2025.7.1~) 기본: **1.5%p** 가산, 다만 **지방 주담대는 0.75%p**만 가산. [금융위원회+1](https://www.fsc.go.kr/no010101/84617?curPage=&srchBeginDt=&srchCtgry=&srchEndDt=&srchKey=&srchText=&utm_source=chatgpt.com)
    - 2025.10.16 “주택시장 안정화 대책”에서

      **수도권·규제지역 주담대 스트레스 금리 하한 1.5% → 3.0%로 상향**. [매일경제+2한겨레+2](https://www.mk.co.kr/news/economy/11441667?utm_source=chatgpt.com)

    - 따라서 **2025.12 기준 시뮬레이터 기본값**:
        - 수도권(규제지역 포함): **+3.0%p**
        - 비수도권: **+0.75%p** (3단계 유예 유지) [금융위원회+1](https://www.fsc.go.kr/no010101/84617?curPage=&srchBeginDt=&srchCtgry=&srchEndDt=&srchKey=&srchText=&utm_source=chatgpt.com)
3. **청년 장래소득 인정 비율 (DSR 산정 시)**

   금융위/언론 자료에서 **5년 단위 연령대별 증가율**이 공개됨: [지디넷코리아+2정책브리핑+2](https://zdnet.co.kr/view/?no=20220623112849&utm_source=chatgpt.com)

    - 만 20~24세 : +**51.6%**
    - 만 25~29세 : +**31.4%**
    - 만 30~34세 : +**13.1%**
    - 만 35세 이상 : 0% (장래소득 인정 없음)

   → 코드에 그대로 반영.

4. **전세대출 DSR 포함**
    - 2025.10.29부터 **1주택자의 수도권·규제지역 전세대출 신규/증액분에 대해 “이자 상환액만 DSR에 포함”**.
    - 기존 전세대출 단순 만기연장은 DSR 미적용. [뱅크몰+2다음+2](https://www.bank-mall.co.kr/plus/blog/12294?utm_source=chatgpt.com)

   → `jeonseIncludedInDsr` 플래그 + **이자만 더하는 로직**으로 구현.


---

## 2. Java 코드

### 2-1. 입력 객체 – `DsrInput.java`

```java
import io.swagger.v3.oas.annotations.media.Schema;

public record DsrInput(

        @Schema(description = "연간 소득 (원)", example = "50000000")
        long annualIncome,

        @Schema(description = "차주 나이 (만 나이)", example = "32")
        int age,

        @Schema(description = "담보 물건지 (SEOUL_METRO: 수도권, ETC: 비수도권)", example = "SEOUL_METRO")
        Region region,

        @Schema(description = "기존 대출 연간 원리금 상환액 (전세대출 제외)", example = "10000000")
        long existingAnnualDebtService,

        @Schema(description = "보유중인 전세대출 잔액 (없으면 0)", example = "200000000")
        long jeonseLoanBalance,

        @Schema(description = "보유중인 전세대출 금리 (%)", example = "4.0")
        double jeonseLoanRate,

        @Schema(description = "신규 대출 목표 금리 방식 (VARIABLE: 변동, MIXED: 혼합, PERIODIC: 주기형, FIXED: 고정)", example = "PERIODIC")
        LoanType targetLoanType,

        @Schema(description = "신규 대출 예상 실제 금리 (%)", example = "4.0")
        double targetLoanRate,

        @Schema(description = "신규 대출 만기 (년)", example = "30")
        int maturityYears,

        @Schema(description = "대출 기관 유형 (BANK: 1금융권, NON_BANK: 제2금융권)", example = "BANK")
        LenderType lenderType,

        @Schema(description = "전세대출 DSR 포함 여부 (정책/상품 특례로 제외되는 경우 false)", example = "true")
        boolean jeonseIncludedInDsr
) {

    public enum Region { SEOUL_METRO, ETC }

    public enum LoanType { VARIABLE, MIXED, PERIODIC, FIXED }

    public enum LenderType { BANK, NON_BANK }
}

```

---

### 2-2. 정책 파라미터 – `DsrPolicy.java`

```java
/**
 * 2025년 하반기(특히 2025.12) 기준 DSR/스트레스 금리/청년 장래소득 정책값을
 * 시뮬레이션에 주입하기 위한 설정 객체.
 *
 * - 실제 정책이 바뀌면 이 클래스/팩토리 메서드만 손보면 됨.
 */
public record DsrPolicy(

        // 1금융권(은행) DSR 한도 (예: 40%)
        double bankDsrLimitRatio,

        // 제2금융권 DSR 한도 (예: 50%)
        double nonBankDsrLimitRatio,

        // 수도권(서울/경기/인천, 규제지역 포함) 주담대 스트레스 금리 기본 가산폭 (단위: %pt)
        // 2025.10.16 이후: 3.0%p 하한
        double seoulMetroStressBase,

        // 비수도권 주담대 스트레스 금리 기본 가산폭 (단위: %pt)
        // 2025.12 기준: 0.75%p (3단계 유예, 이후 상향 가능)
        double nonMetroStressBase,

        // 장래소득 인정 비율 (만 20~24세, +51.6%)
        double youth20to24Multiplier,

        // 장래소득 인정 비율 (만 25~29세, +31.4%)
        double youth25to29Multiplier,

        // 장래소득 인정 비율 (만 30~34세, +13.1%)
        double youth30to34Multiplier,

        // true면 위 multiplier를 사용해 장래소득 계산, false면 현재 소득 그대로 사용
        boolean enableYouthFutureIncome
) {

    /**
     * 1금융권(은행) 기준 2025년 하반기(스트레스 금리 하한 3% 적용 이후) 기본 프로파일.
     *
     * - 은행 DSR 한도   : 40%
     * - 제2금융 DSR 한도: 50%
     * - 수도권 스트레스: +3.0%p
     * - 비수도권 스트레스: +0.75%p (3단계 유예 기준)
     */
    public static DsrPolicy bankDefault2025H2() {
        return new DsrPolicy(
                0.40,   // bankDsrLimitRatio
                0.50,   // nonBankDsrLimitRatio
                3.0,    // seoulMetroStressBase (수도권·규제지역 주담대 ST 금리 하한 3%)
                0.75,   // nonMetroStressBase (지방 주담대 0.75% 유예 기준)
                1.516,  // youth20to24Multiplier (20~24세: +51.6%)
                1.314,  // youth25to29Multiplier (25~29세: +31.4%)
                1.131,  // youth30to34Multiplier (30~34세: +13.1%)
                true    // enableYouthFutureIncome
        );
    }

    /**
     * 2026년에 비수도권도 3단계 ST 금리(예: 1.5%p)로 상향될 경우를 가정한 예시.
     * 실제 발표 후 값 확정되면 이 팩토리를 수정하거나 새로 만들면 됨.
     */
    public static DsrPolicy bankScenario2026_NonMetro15() {
        return new DsrPolicy(
                0.40,
                0.50,
                3.0,   // 수도권 ST 3.0% 유지 가정
                1.5,   // 비수도권도 1.5%p로 상향 가정
                1.516,
                1.314,
                1.131,
                true
        );
    }
}

```

---

### 2-3. 결과 DTO – `DsrResult.java`

```java
/**
 * DSR 시뮬레이션 결과
 */
public record DsrResult(

        // 신규 대출 없이, 현재 기존 대출만 기준으로 한 DSR (%)
        double currentDsrPercent,

        // "이번 계산에서 산출된 최대 한도까지 빌렸다고 가정했을 때" DSR (%)
        // -> 규제상 한도(40% 등)에 얼만큼 붙는지 확인용
        double dsrAfterMaxLoanPercent,

        // DSR 등급 (예: SAFE / WARNING / RESTRICTED)
        String grade,

        // 이번 정책/스트레스 금리 기준으로 산출된 최대 대출 가능액 (원)
        long maxLoanAmount
) { }

```

---

### 2-4. 계산 로직 – `DsrCalculator.java`

```java
/**
 * 정책(DsrPolicy)을 주입받아 DSR/최대 대출 가능액을 계산하는 핵심 클래스.
 */
public final class DsrCalculator {

    private final DsrPolicy policy;

    public DsrCalculator(DsrPolicy policy) {
        this.policy = policy;
    }

    /**
     * DSR 시뮬레이션 실행 및 최대 대출 가능금액 산출.
     * - Step1: 장래소득 반영(선택)
     * - Step2: 기존 부채 + (필요시) 전세대출 이자 반영
     * - Step3: DSR 한도(40%/50%)에서 기존 상환액을 뺀 여유분 계산
     * - Step4: 스트레스 금리(지역+상품유형) 가산 후 PMT 공식 역산으로 최대 원금 계산
     * - Step5: 현재 DSR, 최대 한도까지 빌렸을 때의 DSR까지 같이 리턴
     */
    public DsrResult calculateMaxLoan(DsrInput input) {

        // 1. 소득 산정: 청년층 장래소득 인정
        long recognizedIncome = policy.enableYouthFutureIncome()
                ? calculateRecognizedIncome(input.annualIncome(), input.age(), policy)
                : input.annualIncome();

        if (recognizedIncome <= 0) {
            return new DsrResult(0.0, 0.0, "RESTRICTED", 0L);
        }

        // 2. 기존 부채 + 전세대출 이자 반영
        long jeonseInterest = 0L;
        if (input.jeonseIncludedInDsr() && input.jeonseLoanBalance() > 0 && input.jeonseLoanRate() > 0.0) {
            jeonseInterest = (long) Math.round(
                    input.jeonseLoanBalance() * (input.jeonseLoanRate() / 100.0)
            );
        }

        long totalExistingDebtService = input.existingAnnualDebtService() + jeonseInterest;

        // 3. DSR 한도(금액 기준) → 신규 대출에 쓸 수 있는 여유 한도
        double dsrLimitRatio = switch (input.lenderType()) {
            case BANK -> policy.bankDsrLimitRatio();
            case NON_BANK -> policy.nonBankDsrLimitRatio();
        };

        long maxAllowedTotalDebtService = (long) Math.floor(recognizedIncome * dsrLimitRatio);
        long availableForNewLoanService = Math.max(0L, maxAllowedTotalDebtService - totalExistingDebtService);

        if (availableForNewLoanService <= 0L) {
            double currentDsr = round1(100.0 * totalExistingDebtService / recognizedIncome);
            return new DsrResult(currentDsr, currentDsr, "RESTRICTED", 0L);
        }

        // 4. 스트레스 금리 산출 (지역 + 상품 유형)
        double stressRateToAdd = calculateStressRate(input.region(), input.targetLoanType(), policy);
        double finalStressRate = input.targetLoanRate() + stressRateToAdd;

        // 5. 스트레스 금리로 최대 대출 가능 원금 역산 (원리금 균등)
        long maxLoanPrincipal = calculatePrincipal(availableForNewLoanService, finalStressRate, input.maturityYears());

        if (maxLoanPrincipal <= 0L) {
            double currentDsr = round1(100.0 * totalExistingDebtService / recognizedIncome);
            return new DsrResult(currentDsr, currentDsr, "RESTRICTED", 0L);
        }

        // 6. DSR 계산
        double currentDsr = 100.0 * totalExistingDebtService / recognizedIncome;

        long newLoanAnnualDebtService =
                approximateAnnualDebtService(maxLoanPrincipal, input.targetLoanRate(), input.maturityYears());

        double dsrAfterMaxLoan =
                100.0 * (totalExistingDebtService + newLoanAnnualDebtService) / recognizedIncome;

        double currentDsrRounded = round1(currentDsr);
        double dsrAfterMaxLoanRounded = round1(dsrAfterMaxLoan);

        double dsrLimitPercent = dsrLimitRatio * 100.0;

        String grade;
        if (dsrAfterMaxLoanRounded >= dsrLimitPercent) {
            grade = "RESTRICTED";  // 규제 상한에 도달 또는 초과
        } else if (dsrAfterMaxLoanRounded >= dsrLimitPercent - 5.0) {
            grade = "WARNING";     // 상한 5%p 이내 접근
        } else {
            grade = "SAFE";
        }

        return new DsrResult(
                currentDsrRounded,
                dsrAfterMaxLoanRounded,
                grade,
                maxLoanPrincipal
        );
    }

    // === 내부 유틸 메서드들 ===

    /**
     * 청년 장래소득 계산 (만 20~34세 구간만 적용)
     * - 20~24세 : 현재 소득 * 1.516
     * - 25~29세 : 현재 소득 * 1.314
     * - 30~34세 : 현재 소득 * 1.131
     * - 35세 이상: 장래소득 인정 없음
     *
     * 실제로는 대출 만기나 상품 종류에 따라 인식 비율이 조금 달라질 수 있음.
     */
    private static long calculateRecognizedIncome(long income, int age, DsrPolicy policy) {
        if (age >= 20 && age <= 24) {
            return (long) Math.round(income * policy.youth20to24Multiplier());
        } else if (age >= 25 && age <= 29) {
            return (long) Math.round(income * policy.youth25to29Multiplier());
        } else if (age >= 30 && age <= 34) {
            return (long) Math.round(income * policy.youth30to34Multiplier());
        }
        return income;
    }

    /**
     * 스트레스 금리 계산
     * - 수도권 주담대: 2025.10.16 이후 ST 금리 하한 3.0%p 가정
     * - 비수도권 주담대: 2025.12 기준 0.75%p (3단계 유예)
     * - 상품 유형별 가중치:
     *   - VARIABLE(변동): 100% 반영
     *   - MIXED(혼합):   70% 반영 (장기 고정 비중이 어느 정도 있는 혼합형 가정)
     *   - PERIODIC(주기형): 40% 반영 (조정주기가 비교적 긴 주기형 가정)
     *   - FIXED(순수 고정): 0%
     *
     * 실제 은행/상품에 따라 이 비율은 다를 수 있으므로,
     * 필요시 factor 값만 조정해서 실제 취급 상품과 맞출 수 있음.
     */
    private static double calculateStressRate(DsrInput.Region region,
                                              DsrInput.LoanType type,
                                              DsrPolicy policy) {

        double base = (region == DsrInput.Region.SEOUL_METRO)
                ? policy.seoulMetroStressBase()
                : policy.nonMetroStressBase();

        double factor = switch (type) {
            case VARIABLE -> 1.0;  // 변동: 100% 반영
            case MIXED -> 0.7;     // 혼합: 70% 반영 (예시)
            case PERIODIC -> 0.4;  // 주기형: 40% 반영 (예시)
            case FIXED -> 0.0;     // 순수 고정: 스트레스 금리 없음
        };

        return base * factor;
    }

    /**
     * 연간 원리금 상환 가능액(스트레스 금리 기준)을 바탕으로
     * 원리금 균등상환 대출의 최대 원금(PV)을 계산.
     *
     * annualPayment : 스트레스 금리 기준 한도 내 연간 상환 가능액
     * annualRatePercent : 스트레스 금리가 적용된 가상 연이자율
     * years : 만기(년)
     */
    private static long calculatePrincipal(long annualPayment, double annualRatePercent, int years) {
        double monthlyRate = (annualRatePercent / 100.0) / 12.0;
        int totalMonths = years * 12;
        double monthlyPayment = annualPayment / 12.0;

        if (monthlyRate <= 0.0) {
            // 이론적으로는 거의 없지만, 방어 로직: 이자 0%라면 단순히 원금 = 월상환액 * 개월수
            return (long) Math.floor(monthlyPayment * totalMonths);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        return (long) Math.floor(monthlyPayment * pvFactor);
    }

    /**
     * 실제 금리 기준 신규 대출 원리금 연간 상환액 추정.
     * - 여기서는 스트레스 금리가 아니라 "실제 targetLoanRate"로 계산.
     * - DSR After Max Loan 계산에 사용.
     */
    private static long approximateAnnualDebtService(long principal,
                                                     double annualRatePercent,
                                                     int years) {
        double monthlyRate = (annualRatePercent / 100.0) / 12.0;
        int totalMonths = years * 12;

        if (totalMonths <= 0) {
            return 0L;
        }

        if (monthlyRate <= 0.0) {
            // 이자 0% 방어: 단순 원금 균등
            double monthlyPayment = principal / (double) totalMonths;
            return (long) Math.round(monthlyPayment * 12.0);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        double monthlyPayment = principal / pvFactor;

        return (long) Math.round(monthlyPayment * 12.0);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

```

---

### 2-5. 사용 예시 – `DsrSimulationMain.java`

```java
public class DsrSimulationMain {

    public static void main(String[] args) {

        // 정책 프로파일 선택 (2025년 12월, 1금융권 기준)
        DsrPolicy policy = DsrPolicy.bankDefault2025H2();
        DsrCalculator calculator = new DsrCalculator(policy);

        // 상황: 서울 거주 32세 직장인, 연봉 6천만원
        // 기존 빚: 기타 대출 연 300만원 + 전세대출 2억(금리 4%) 보유
        // 전세대출은 1주택자 수도권 신규 전세대출이라고 가정 → 이자 DSR 포함
        // 목표: 40년 만기 주담대, 금리 4.0%

        DsrInput variableInput = new DsrInput(
                60_000_000L,                        // annualIncome
                32,                                 // age
                DsrInput.Region.SEOUL_METRO,        // region
                3_000_000L,                         // existingAnnualDebtService (전세 제외)
                200_000_000L,                       // jeonseLoanBalance
                4.0,                                // jeonseLoanRate
                DsrInput.LoanType.VARIABLE,         // targetLoanType (변동)
                4.0,                                // targetLoanRate
                40,                                 // maturityYears
                DsrInput.LenderType.BANK,           // 1금융권
                true                                // jeonseIncludedInDsr (전세 이자 DSR 포함)
        );

        DsrResult variableResult = calculator.calculateMaxLoan(variableInput);

        System.out.println("=== 2026년(2025.12 정책 기준) DSR 시뮬레이션 결과 – 변동금리 ===");
        printResult(variableResult);

        // 비교: 동일 조건에서 주기형 상품 선택 (금리 4.0% 동일, 스트레스 금리만 완화)
        DsrInput periodicInput = new DsrInput(
                60_000_000L,
                32,
                DsrInput.Region.SEOUL_METRO,
                3_000_000L,
                200_000_000L,
                4.0,
                DsrInput.LoanType.PERIODIC,        // 주기형
                4.0,
                40,
                DsrInput.LenderType.BANK,
                true
        );

        DsrResult periodicResult = calculator.calculateMaxLoan(periodicInput);

        System.out.println("\n--- 주기형 상품 선택 시 ---");
        printResult(periodicResult);

        long diff = periodicResult.maxLoanAmount() - variableResult.maxLoanAmount();
        System.out.println("\n>> 최대 대출 가능액 차이: " + formatMoney(diff));
    }

    private static void printResult(DsrResult result) {
        System.out.println("현재 DSR (기존 대출 기준): " +
                String.format("%.1f%%", result.currentDsrPercent()));
        System.out.println("최대 한도까지 빌렸을 때 DSR: " +
                String.format("%.1f%%", result.dsrAfterMaxLoanPercent()));
        System.out.println("DSR 등급: " + result.grade());
        System.out.println("최대 대출 가능액: " + formatMoney(result.maxLoanAmount()));
    }

    private static String formatMoney(long amount) {
        return String.format("%,d원", amount);
    }
}

```

---

## 3. 어떻게 쓰면 좋을지 (요약)

- **정책 타임라인별 시나리오**
    - 2025.12 ~ (정확히 언제까지일지 모르는) 현 시점:

      → `DsrPolicy.bankDefault2025H2()` 사용 (수도권 ST 3.0, 지방 ST 0.75)

    - 2026년에 비수도권 ST 금리가 1.5%나 3.0%로 바뀌면

      → `DsrPolicy.bankScenario2026_NonMetro15()` 같이 새 팩토리 만들어서 쓰기

- **상품별 미세 튜닝**
    - 스트레스 비율 `factor` (0.7, 0.4 등)를

      실제 은행/상품 설명서 보고 맞춰주면, 거의 실전 수준의 시뮬레이터가 된다.

- **전세대출**
    - `jeonseIncludedInDsr = true` : 1주택자 수도권 전세대출 신규/증액 (이자 포함)
    - `= false` : 기존 전세 연장, 특례상품 등 **예외 케이스** 시뮬레이션용