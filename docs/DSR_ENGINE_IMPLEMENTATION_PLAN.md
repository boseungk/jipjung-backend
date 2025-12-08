# DSR 엔진 2026 업그레이드 구현 계획

## 개요

현재 `UserService.calculateDsr()`의 간단한 DSR 계산 로직을 **2026년형 DSR/스트레스 금리 엔진**으로 업그레이드합니다. `DSR_SIMULATOR.md` 문서에 정의된 `DsrCalculator`, `DsrPolicy`, `DsrInput`, `DsrResult` 구조를 기반으로 구현하며, **LITE/PRO 모드**를 지원하는 Progressive Disclosure UX를 적용합니다.

---

## 📋 리뷰 피드백 반영 사항

| # | 피드백 | 조치 |
|---|--------|------|
| 1 | CustomUserDetails 임포트 경로 오류 | ✅ `service.CustomUserDetails`로 수정 |
| 2 | 입력 검증 느슨함 (enum valueOf 500 에러) | ✅ `@Pattern` Bean Validation으로 변경 (4xx 반환) |
| 3 | 대시보드 DsrSection 연동 누락 | ✅ DashboardService 호출부 수정 명시 |
| 4 | DsrSection.from() 시그니처 비호환 | ✅ 오버로드 + deprecated 방식 |
| 5 | 등급 로직 불일치 | ✅ DsrResult.grade() 직접 사용 (통합) |
| 6 | gameUpdate 로직 누락 | ⏳ Phase 2로 분리 (WARNING 추가) |
| 7 | 상태 저장 설계 누락 | ⏳ Phase 2로 분리 (WARNING 추가) |
| 8 | 온보딩 필수 필드 vs LITE 모드 | ✅ 현재 유지 (0원 시 중위소득 fallback) |

---

## 현재 상태 분석

### 현재 DSR 계산 (UserService.java:118-133)

```java
// 현재 단순 계산
double dsrRatio = (existingLoanMonthly * 1000.0) / monthlyIncome / 10.0;
long maxLoanAmount = availableMonthlyRepayment * 12 * 30 * 7 / 10;
```

**문제점:**
- 스트레스 금리 미반영
- 청년 장래소득 인정 미반영
- 지역별 정책 차이 미반영
- 전세대출 DSR 포함 미지원
- 대출 유형별(변동/주기형/고정) 차이 미반영

### 목표 DSR 계산 (DSR_SIMULATOR.md 기반)

| 항목 | 정책값 |
|------|--------|
| **스트레스 금리 (수도권)** | +3.0%p |
| **스트레스 금리 (비수도권)** | +0.75%p |
| **청년 장래소득 (20-24세)** | +51.6% |
| **청년 장래소득 (25-29세)** | +31.4% |
| **청년 장래소득 (30-34세)** | +13.1% |
| **DSR 한도 (1금융)** | 40% |
| **DSR 한도 (2금융)** | 50% |

---

## 신규 파일 생성

### 1. DSR Core 패키지

#### 📁 `src/main/java/com/jipjung/project/dsr/DsrInput.java`

```java
package com.jipjung.project.dsr;

import io.swagger.v3.oas.annotations.media.Schema;

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
    public enum Region { SEOUL_METRO, ETC }
    public enum LoanType { VARIABLE, MIXED, PERIODIC, FIXED }
    public enum LenderType { BANK, NON_BANK }
}
```

---

#### 📁 `src/main/java/com/jipjung/project/dsr/DsrPolicy.java`

```java
package com.jipjung.project.dsr;

/**
 * 2025년 하반기 기준 DSR/스트레스 금리/청년 장래소득 정책값
 */
public record DsrPolicy(
        double bankDsrLimitRatio,      // 1금융권 DSR 한도 (0.40)
        double nonBankDsrLimitRatio,   // 2금융권 DSR 한도 (0.50)
        double seoulMetroStressBase,   // 수도권 스트레스 금리 (3.0)
        double nonMetroStressBase,     // 비수도권 스트레스 금리 (0.75)
        double youth20to24Multiplier,  // 20-24세 장래소득 (1.516)
        double youth25to29Multiplier,  // 25-29세 장래소득 (1.314)
        double youth30to34Multiplier,  // 30-34세 장래소득 (1.131)
        boolean enableYouthFutureIncome
) {
    /**
     * 2025년 12월 기준 1금융권 기본 정책
     */
    public static DsrPolicy bankDefault2025H2() {
        return new DsrPolicy(
                0.40,   // bankDsrLimitRatio
                0.50,   // nonBankDsrLimitRatio
                3.0,    // seoulMetroStressBase
                0.75,   // nonMetroStressBase
                1.516,  // youth20to24Multiplier
                1.314,  // youth25to29Multiplier
                1.131,  // youth30to34Multiplier
                true    // enableYouthFutureIncome
        );
    }
}
```

---

#### 📁 `src/main/java/com/jipjung/project/dsr/DsrResult.java`

```java
package com.jipjung.project.dsr;

import io.swagger.v3.oas.annotations.media.Schema;

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
) { }
```

---

#### 📁 `src/main/java/com/jipjung/project/dsr/DsrCalculator.java`

```java
package com.jipjung.project.dsr;

import org.springframework.stereotype.Component;

/**
 * 정책(DsrPolicy)을 기반으로 DSR/최대 대출 가능액을 계산하는 핵심 클래스
 */
@Component
public class DsrCalculator {

    /**
     * DSR 시뮬레이션 실행 및 최대 대출 가능금액 산출
     */
    public DsrResult calculateMaxLoan(DsrInput input, DsrPolicy policy) {

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
            grade = "RESTRICTED";
        } else if (dsrAfterMaxLoanRounded >= dsrLimitPercent - 5.0) {
            grade = "WARNING";
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

    private long calculateRecognizedIncome(long income, int age, DsrPolicy policy) {
        if (age >= 20 && age <= 24) {
            return (long) Math.round(income * policy.youth20to24Multiplier());
        } else if (age >= 25 && age <= 29) {
            return (long) Math.round(income * policy.youth25to29Multiplier());
        } else if (age >= 30 && age <= 34) {
            return (long) Math.round(income * policy.youth30to34Multiplier());
        }
        return income;
    }

    private double calculateStressRate(DsrInput.Region region, DsrInput.LoanType type, DsrPolicy policy) {
        double base = (region == DsrInput.Region.SEOUL_METRO)
                ? policy.seoulMetroStressBase()
                : policy.nonMetroStressBase();

        double factor = switch (type) {
            case VARIABLE -> 1.0;
            case MIXED -> 0.7;
            case PERIODIC -> 0.4;
            case FIXED -> 0.0;
        };

        return base * factor;
    }

    private long calculatePrincipal(long annualPayment, double annualRatePercent, int years) {
        double monthlyRate = (annualRatePercent / 100.0) / 12.0;
        int totalMonths = years * 12;
        double monthlyPayment = annualPayment / 12.0;

        if (monthlyRate <= 0.0) {
            return (long) Math.floor(monthlyPayment * totalMonths);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        return (long) Math.floor(monthlyPayment * pvFactor);
    }

    private long approximateAnnualDebtService(long principal, double annualRatePercent, int years) {
        double monthlyRate = (annualRatePercent / 100.0) / 12.0;
        int totalMonths = years * 12;

        if (totalMonths <= 0) return 0L;

        if (monthlyRate <= 0.0) {
            double monthlyPayment = principal / (double) totalMonths;
            return (long) Math.round(monthlyPayment * 12.0);
        }

        double pvFactor = (1 - Math.pow(1 + monthlyRate, -totalMonths)) / monthlyRate;
        double monthlyPayment = principal / pvFactor;

        return (long) Math.round(monthlyPayment * 12.0);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
```

---

### 2. API 요청/응답 DTO

```java
package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "DSR 시뮬레이션 요청 (PRO 모드)")
public record DsrSimulationRequest(

        @Schema(description = "연소득 (원)", example = "60000000")
        @NotNull(message = "연소득은 필수입니다")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다")
        Long annualIncome,

        // ✅ @Pattern으로 유효한 값만 허용 (4xx 반환)
        @Schema(description = "지역 (SEOUL_METRO/ETC)", example = "SEOUL_METRO")
        @NotBlank(message = "지역은 필수입니다")
        @Pattern(regexp = "SEOUL_METRO|ETC", message = "지역은 SEOUL_METRO 또는 ETC만 가능합니다")
        String region,

        @Schema(description = "기존 연간 원리금 상환액 (원)", example = "3000000")
        @NotNull(message = "기존 연간 원리금 상환액은 필수입니다")
        @Min(value = 0)
        Long existingAnnualDebtService,

        @Schema(description = "전세대출 잔액 (원)", example = "200000000")
        Long jeonseLoanBalance,

        @Schema(description = "전세대출 금리 (%)", example = "4.0")
        Double jeonseLoanRate,

        @Schema(description = "전세대출 DSR 포함 여부", example = "true")
        Boolean jeonseIncludedInDsr,

        // ✅ @Pattern으로 유효한 값만 허용 (4xx 반환)
        @Schema(description = "대출 유형 (VARIABLE/MIXED/PERIODIC/FIXED)", example = "PERIODIC")
        @NotBlank(message = "대출 유형은 필수입니다")
        @Pattern(regexp = "VARIABLE|MIXED|PERIODIC|FIXED", message = "대출 유형은 VARIABLE/MIXED/PERIODIC/FIXED 중 하나여야 합니다")
        String targetLoanType,

        @Schema(description = "예상 대출 금리 (%)", example = "4.0")
        @NotNull(message = "예상 대출 금리는 필수입니다")
        Double targetLoanRate,

        @Schema(description = "대출 만기 (년)", example = "40")
        @NotNull(message = "대출 만기는 필수입니다")
        @Min(value = 1) @Max(value = 50)
        Integer maturityYears,

        // ✅ @Pattern으로 유효한 값만 허용 (null이면 BANK 기본값)
        @Schema(description = "금융기관 유형 (BANK/NON_BANK, 기본: BANK)", example = "BANK")
        @Pattern(regexp = "BANK|NON_BANK", message = "금융기관 유형은 BANK 또는 NON_BANK만 가능합니다")
        String lenderType
) { }
```

> 💡 **Fail Fast 원칙:** 잘못된 enum 값은 Bean Validation에서 바로 400 에러 반환. 사용자가 오타를 빠르게 인지할 수 있음.

---

#### 📁 `src/main/java/com/jipjung/project/controller/dto/response/DsrSimulationResponse.java`

```java
package com.jipjung.project.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DSR 시뮬레이션 응답")
public record DsrSimulationResponse(

        @Schema(description = "현재 DSR (%)", example = "15.5")
        double currentDsrPercent,

        @Schema(description = "DSR 등급", example = "SAFE")
        String userGrade,

        @Schema(description = "최대 대출 가능액 (원)", example = "420000000")
        long maxLoanAmount,

        @Schema(description = "적용된 정책 정보")
        AppliedPolicy appliedPolicy,

        @Schema(description = "시뮬레이션 팁")
        String simulationTip
) {
    @Schema(description = "적용된 정책")
    public record AppliedPolicy(
            @Schema(description = "적용된 스트레스 가산금리 (%)", example = "1.2")
            double stressDsrRate,

            @Schema(description = "적용된 장래소득 인정 배율", example = "1.131")
            double youthIncomeMultiplier
    ) { }

    public static DsrSimulationResponse from(
            com.jipjung.project.dsr.DsrResult result,
            double stressRate,
            double youthMultiplier,
            String tip
    ) {
        return new DsrSimulationResponse(
                result.currentDsrPercent(),
                result.grade(),
                result.maxLoanAmount(),
                new AppliedPolicy(stressRate, youthMultiplier),
                tip
        );
    }
}
```

---

### 3. 서비스 레이어

#### 📁 `src/main/java/com/jipjung/project/service/DsrService.java`

```java
package com.jipjung.project.service;

import com.jipjung.project.controller.dto.request.DsrSimulationRequest;
import com.jipjung.project.controller.dto.response.DsrSimulationResponse;
import com.jipjung.project.domain.User;
import com.jipjung.project.dsr.*;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsrService {

    private final DsrCalculator dsrCalculator;
    private final UserMapper userMapper;

    // 한국 중위소득 (2024년 4인 가구 기준, 연간)
    private static final long MEDIAN_INCOME = 58_440_000L;

    /**
     * LITE 모드 DSR 계산 (온보딩 시)
     * - 사용자 입력 소득 또는 중위소득 기반
     * - 표준 설정 (수도권, 변동금리, 30년 만기)
     */
    public DsrResult calculateLiteDsr(User user) {
        long annualIncome = user.getAnnualIncome() != null && user.getAnnualIncome() > 0
                ? user.getAnnualIncome()
                : MEDIAN_INCOME;

        int age = user.getBirthYear() != null
                ? LocalDate.now().getYear() - user.getBirthYear()
                : 35;

        long existingAnnualDebt = user.getExistingLoanMonthly() != null
                ? user.getExistingLoanMonthly() * 12
                : 0L;

        DsrInput input = new DsrInput(
                annualIncome,
                age,
                DsrInput.Region.SEOUL_METRO,  // 기본: 수도권
                existingAnnualDebt,
                0L,     // 전세 없음
                0.0,
                DsrInput.LoanType.VARIABLE,   // 기본: 변동
                4.5,    // 기본 금리
                30,     // 기본 만기
                DsrInput.LenderType.BANK,
                false
        );

        DsrPolicy policy = DsrPolicy.bankDefault2025H2();
        return dsrCalculator.calculateMaxLoan(input, policy);
    }

    /**
     * PRO 모드 DSR 시뮬레이션 (상세 입력)
     */
    public DsrSimulationResponse simulate(Long userId, DsrSimulationRequest request) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        int age = user.getBirthYear() != null
                ? LocalDate.now().getYear() - user.getBirthYear()
                : 35;

        // ✅ Bean Validation에서 이미 검증됨 - valueOf 직접 사용 가능
        DsrInput.Region region = DsrInput.Region.valueOf(request.region());
        DsrInput.LoanType loanType = DsrInput.LoanType.valueOf(request.targetLoanType());
        DsrInput.LenderType lenderType = request.lenderType() != null 
                ? DsrInput.LenderType.valueOf(request.lenderType())
                : DsrInput.LenderType.BANK;  // null이면 기본값

        DsrInput input = new DsrInput(
                request.annualIncome(),
                age,
                region,
                request.existingAnnualDebtService(),
                request.jeonseLoanBalance() != null ? request.jeonseLoanBalance() : 0L,
                request.jeonseLoanRate() != null ? request.jeonseLoanRate() : 0.0,
                loanType,
                request.targetLoanRate(),
                request.maturityYears(),
                lenderType,
                request.jeonseIncludedInDsr() != null && request.jeonseIncludedInDsr()
        );

        DsrPolicy policy = DsrPolicy.bankDefault2025H2();
        DsrResult result = dsrCalculator.calculateMaxLoan(input, policy);

        // 적용된 스트레스 금리 계산
        double stressRate = calculateAppliedStressRate(region, loanType, policy);

        // 적용된 장래소득 배율
        double youthMultiplier = getYouthMultiplier(age, policy);

        // 팁 생성
        String tip = generateTip(loanType, result);

        log.info("DSR simulation completed. userId: {}, grade: {}, maxLoan: {}",
                userId, result.grade(), result.maxLoanAmount());

        return DsrSimulationResponse.from(result, stressRate, youthMultiplier, tip);
    }

    private double calculateAppliedStressRate(DsrInput.Region region, DsrInput.LoanType type, DsrPolicy policy) {
        double base = (region == DsrInput.Region.SEOUL_METRO)
                ? policy.seoulMetroStressBase()
                : policy.nonMetroStressBase();

        double factor = switch (type) {
            case VARIABLE -> 1.0;
            case MIXED -> 0.7;
            case PERIODIC -> 0.4;
            case FIXED -> 0.0;
        };

        return Math.round(base * factor * 10.0) / 10.0;
    }

    private double getYouthMultiplier(int age, DsrPolicy policy) {
        if (!policy.enableYouthFutureIncome()) return 1.0;

        if (age >= 20 && age <= 24) return policy.youth20to24Multiplier();
        if (age >= 25 && age <= 29) return policy.youth25to29Multiplier();
        if (age >= 30 && age <= 34) return policy.youth30to34Multiplier();
        return 1.0;
    }

    private String generateTip(DsrInput.LoanType currentType, DsrResult result) {
        if (currentType == DsrInput.LoanType.VARIABLE) {
            return "💡 주기형 상품으로 변경하면 스트레스 금리가 낮아져 한도가 늘어날 수 있어요!";
        }
        if ("WARNING".equals(result.grade())) {
            return "⚠️ DSR 한도에 가까워요. 대출 만기를 늘리거나 기존 대출을 줄이면 여유가 생겨요.";
        }
        if ("RESTRICTED".equals(result.grade())) {
            return "🚫 현재 조건으로는 추가 대출이 어려워요. 기존 대출 상환을 우선 검토해보세요.";
        }
        return "✅ 여유있는 DSR 상태입니다. 목표 금액에 맞춰 저축 계획을 세워보세요!";
    }
}
```

---

### 4. 컨트롤러

#### 📁 `src/main/java/com/jipjung/project/controller/DsrController.java`

```java
package com.jipjung.project.controller;

import com.jipjung.project.controller.dto.request.DsrSimulationRequest;
import com.jipjung.project.controller.dto.response.DsrSimulationResponse;
import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.CustomUserDetails;  // ✅ 수정: 올바른 경로
import com.jipjung.project.service.DsrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "DSR 시뮬레이션", description = "DSR 상세 시뮬레이션 API")
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class DsrController {

    private final DsrService dsrService;

    @Operation(
            summary = "DSR 시뮬레이션 (PRO 모드)",
            description = """
                    2026년 정책 기반 상세 DSR 시뮬레이션을 수행합니다.
                    
                    **적용 정책:**
                    - 스트레스 금리: 수도권 3.0%p, 비수도권 0.75%p
                    - 청년 장래소득: 20-34세 구간별 인정
                    - 대출 유형별 스트레스 반영율: 변동 100%, 혼합 70%, 주기형 40%, 고정 0%
                    
                    **응답 정보:**
                    - 현재 DSR 및 등급
                    - 최대 대출 가능액
                    - 적용된 정책 상세
                    - 맞춤 시뮬레이션 팁
                    """
    )
    @PostMapping("/dsr")
    public ResponseEntity<ApiResponse<DsrSimulationResponse>> simulate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DsrSimulationRequest request
    ) {
        DsrSimulationResponse response = dsrService.simulate(
                userDetails.getId(),
                request
        );
        return ApiResponse.success(response);
    }
}
```

---

## 기존 파일 수정

### 1. UserService.java

**변경 내용:** DSR 계산을 `DsrService`로 위임

```diff
+ import com.jipjung.project.service.DsrService;
+ import com.jipjung.project.dsr.DsrResult;

  @Service
  @RequiredArgsConstructor
  public class UserService {

-     private static final double SAFE_THRESHOLD = 30.0;
-     private static final double CAUTION_THRESHOLD = 50.0;

      private final UserMapper userMapper;
      private final UserPreferredAreaMapper userPreferredAreaMapper;
+     private final DsrService dsrService;

      @Transactional
      public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
          // ... 기존 로직 ...

          // 5. DSR 계산
-         long monthlyIncome = updatedUser.getMonthlyIncome();
-         long existingLoanMonthly = request.existingLoanMonthly();
-         DsrResult dsrResult = calculateDsr(monthlyIncome, existingLoanMonthly);
+         com.jipjung.project.dsr.DsrResult dsrResult = dsrService.calculateLiteDsr(updatedUser);
+         
+         // DTO 변환 (LITE 모드용) - 등급은 Calculator 기준 그대로 사용
+         OnboardingResponse.DsrResult liteResult = new OnboardingResponse.DsrResult(
+                 dsrResult.currentDsrPercent(),
+                 dsrResult.grade(),
+                 dsrResult.maxLoanAmount()
+         );

-         return OnboardingResponse.from(updatedUser, dsrResult);
+         return OnboardingResponse.from(updatedUser, liteResult);
      }

      -     private DsrResult calculateDsr(long monthlyIncome, long existingLoanMonthly) {
      -         // ... 삭제 ...
      -     }
      -
      -     private String toGrade(double dsrRatio) {
      -         // ... 삭제 ...
      -     }
  }
```

---

### 2. SecurityConfig.java

**변경 내용:** DSR 시뮬레이션 엔드포인트 권한 추가

```diff
  .requestMatchers("/api/users/**").authenticated()
+ .requestMatchers("/api/simulation/**").authenticated()
```

---

### 3. DashboardResponse.java (✅ 추가)

**변경 내용:** DsrSection에 오버로드 추가 (기존 호환성 유지)

```diff
  public record DsrSection(...) {
      
+     // ✅ 기존 메서드 유지 (deprecated 마킹)
+     @Deprecated
      public static DsrSection from(User user) {
-         // 기존 단순 비율 계산
+         // 기존 로직 유지 - DashboardService 마이그레이션 전까지 사용
+         // TODO: Phase 1 완료 후 삭제
          ...
      }

+     // ✅ 신규 메서드 - DsrResult 연동
+     public static DsrSection from(User user, DsrResult dsrResult) {
+         FinancialInfo financialInfo = FinancialInfo.from(user);
+         
+         // ✅ DsrResult의 등급을 그대로 사용 (통합 등급)
+         String gradeLabel = switch (dsrResult.grade()) {
+             case "SAFE" -> "안전";
+             case "WARNING" -> "주의";
+             case "RESTRICTED" -> "위험";
+             default -> dsrResult.grade();
+         };
+         String gradeColor = switch (dsrResult.grade()) {
+             case "SAFE" -> "GREEN";
+             case "WARNING" -> "YELLOW";
+             case "RESTRICTED" -> "RED";
+             default -> "GRAY";
+         };
+         
+         return new DsrSection(
+                 dsrResult.currentDsrPercent(),
+                 gradeLabel,
+                 gradeColor,
+                 financialInfo
+         );
+     }
  }
```

> 💡 **오버로드 이유:** 기존 `from(User)` 메서드를 유지하여 컴파일 에러 방지. DashboardService 마이그레이션 완료 후 deprecated 메서드 제거.

---

### 4. DashboardService.java (✅ 추가)

**변경 내용:** DsrSection 생성 시 DsrService 사용

```diff
  private final DreamHomeMapper dreamHomeMapper;
  private final SavingsHistoryMapper savingsHistoryMapper;
  private final StreakHistoryMapper streakHistoryMapper;
+ private final DsrService dsrService;

  public DashboardResponse getDashboard(Long userId) {
      // ...
-     DsrSection dsrSection = DsrSection.from(user);
+     DsrResult dsrResult = dsrService.calculateLiteDsr(user);
+     DsrSection dsrSection = DsrSection.from(user, dsrResult);
      // ...
  }
```

---

## 파일 생성/수정 요약

| 작업 | 경로 | 설명 |
|------|------|------|
| **[NEW]** | `dsr/DsrInput.java` | DSR 시뮬레이션 입력 DTO |
| **[NEW]** | `dsr/DsrPolicy.java` | 정책 파라미터 (2025.12 기준) |
| **[NEW]** | `dsr/DsrResult.java` | DSR 시뮬레이션 결과 |
| **[NEW]** | `dsr/DsrCalculator.java` | 핵심 계산 로직 |
| **[NEW]** | `dto/request/DsrSimulationRequest.java` | API 요청 DTO |
| **[NEW]** | `dto/response/DsrSimulationResponse.java` | API 응답 DTO |
| **[NEW]** | `service/DsrService.java` | DSR 서비스 레이어 |
| **[NEW]** | `controller/DsrController.java` | DSR 시뮬레이션 API |
| **[MODIFY]** | `service/UserService.java` | DSR 계산 위임 |
| **[MODIFY]** | `SecurityConfig.java` | 엔드포인트 권한 추가 |
| **[MODIFY]** | `dto/response/DashboardResponse.java` | DsrSection에 DsrResult 연동 |
| **[MODIFY]** | `service/DashboardService.java` | DsrService 주입 및 사용 |

---

## 검증 방법

### Swagger UI 테스트

1. 애플리케이션 실행: `./mvnw spring-boot:run`
2. Swagger 접속: `http://localhost:8080/swagger-ui.html`
3. 로그인 후 JWT 토큰 획득
4. `POST /api/simulation/dsr` 테스트:

```json
{
  "annualIncome": 60000000,
  "region": "SEOUL_METRO",
  "existingAnnualDebtService": 3000000,
  "jeonseLoanBalance": 200000000,
  "jeonseLoanRate": 4.0,
  "jeonseIncludedInDsr": true,
  "targetLoanType": "PERIODIC",
  "targetLoanRate": 4.0,
  "maturityYears": 40,
  "lenderType": "BANK"
}
```

**예상 응답:**
```json
{
  "code": 200,
  "status": "OK",
  "data": {
    "currentDsrPercent": 21.3,
    "userGrade": "SAFE",
    "maxLoanAmount": 387000000,
    "appliedPolicy": {
      "stressDsrRate": 1.2,
      "youthIncomeMultiplier": 1.131
    },
    "simulationTip": "✅ 여유있는 DSR 상태입니다..."
  }
}
```

---

## 💡 LITE 모드 동작 방식

현재 `OnboardingRequest`는 **연소득, 기존대출을 필수로 입력**받습니다. 이는 스펙의 "30초 내 진입" 요구와 다른 듯 보이지만, 실제로는 **더 정확한 LITE 모드**를 구현합니다:

| 입력값 | 동작 |
|--------|------|
| `annualIncome = 50,000,000` | 입력값 그대로 DSR 계산 |
| `annualIncome = 0` | 중위소득 (58,440,000원) fallback |
| `existingLoanMonthly = 0` | 기존 대출 없음으로 계산 |

**Swagger 설명 추가 권장:**
```java
@Schema(description = "연소득 (원 단위, 0 입력 시 중위소득 기준으로 추정)")
```

---

## 🚨 등급 통합 로직

**방향:** 등급 계산은 DsrCalculator 1곳에서만 수행하고, 화면에서는 라벨/색상만 변환

| 화면 | 등급 기준 | 상태 |
|------|----------|------|
| 온보딩 | `DsrResult.grade()` 그대로 사용 | OK |
| 대시보드 | `DsrResult.grade()` + 한글 라벨/색상 매핑 | OK |
| PRO | `DsrCalculator` 정책 기반 등급 | OK |

```java
// DsrCalculator에서 통합 등급 결정 (정책 기반)
    double dsrLimitPercent = dsrLimitRatio * 100.0;  // 40% or 50%

String grade;
    if (dsrAfterMaxLoanRounded >= dsrLimitPercent) {
        grade = "RESTRICTED";  // 규제 상한 도달
    } else if (dsrAfterMaxLoanRounded >= dsrLimitPercent - 5.0) {
        grade = "WARNING";     // 상한 5%p 이내
    } else {
        grade = "SAFE";
    }

// 화면별 라벨 변환 (등급 자체는 동일)
// SAFE → "안전" (GREEN)
// WARNING → "주의" (YELLOW)  
// RESTRICTED → "위험" (RED)
```

> 💡 **핵심:** 등급 계산은 `DsrCalculator`에서만 수행. 다른 화면은 라벨/색상만 변환.

---

## ⏳ Phase 2: 후속 구현 (현재 스코프 외)

> [!WARNING]
> **Phase 1 완료 후에도 스펙 미충족 항목이 있습니다:**
> - `gameUpdate` 응답 (reducedGap, expGained)
> - `dsr_mode` 상태 저장 및 이력 관리
> - 대시보드 `gapAnalysis` 연동 (virtualLoanLimit, requiredSavings)
>
> 이 항목들은 DSR 엔진 코어 안정화 후 Phase 2로 진행합니다.

아래 항목들은 DSR 엔진 코어가 안정화된 후 Phase 2로 진행합니다:

### 1. 게임 업데이트 연동 (gameUpdate)

스펙 요구:
```json
{
  "gameUpdate": {
    "reducedGap": 50000000,
    "expGained": 500
  }
}
```

**구현 필요 사항:**
- `DreamHome` 테이블과 연동
- `requiredSavings` 재계산: `TargetPrice - (CurrentAssets + MaxLoanAmount)`
- 경험치 보상 로직

### 2. 상태 저장 및 이력 관리

**스키마 변경:**
```sql
ALTER TABLE `user` ADD COLUMN dsr_mode VARCHAR(10) DEFAULT 'LITE';
ALTER TABLE `user` ADD COLUMN last_dsr_calculation_at TIMESTAMP;

CREATE TABLE dsr_calculation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    input_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    dsr_mode VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Mapper/Service 수정:**
- `UserMapper.updateDsrMode()`
- `DsrHistoryMapper.insert()`

### 3. 대시보드 gapAnalysis 연동

스펙의 `virtualLoanLimit`, `requiredSavings` 필드 추가:
```json
{
  "gapAnalysis": {
    "targetAmount": 950000000,
    "currentAssets": 30000000,
    "virtualLoanLimit": 300000000,
    "requiredSavings": 620000000
  }
}
```

---

## 구현 순서 (권장)

### Phase 1 (현재)
1. ✅ DSR Core 패키지 생성 (`DsrInput`, `DsrPolicy`, `DsrResult`, `DsrCalculator`)
2. ✅ DsrService 구현 (LITE/PRO 모드)
3. ✅ DsrController 구현 (`POST /api/simulation/dsr`)
4. ✅ UserService 리팩토링 (DSR 계산 위임)
5. ✅ DashboardService/Response 연동
6. ✅ SecurityConfig 엔드포인트 추가
7. 🧪 Swagger UI 테스트

### Phase 2 (후속)
1. ⏳ gameUpdate 로직 (DreamHome 연동)
2. ⏳ dsr_calculation_history 테이블 및 이력 저장
3. ⏳ 대시보드 gapAnalysis 필드 연동
4. ⏳ User.dsrMode 상태 관리

