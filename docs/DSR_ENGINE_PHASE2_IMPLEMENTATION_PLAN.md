# DSR 엔진 Phase 2 구현 계획

## 개요

Phase 1에서 DSR 코어 엔진(`DsrCalculator`, `DsrPolicy`, `DsrInput`, `DsrResult`)과 API(`DsrController`, `DsrService`)가 완성되었습니다.

**Phase 2 목표:** DSR 시뮬레이션 결과를 **게임 시스템과 연동**하여 사용자에게 보상을 제공합니다.

---

## 🎮 게임 연동이란?

```
[DSR 시뮬레이션 전]
목표 아파트: 9.5억
현재 자산: 3천만원 (온보딩 입력)
대출 한도 (LITE): 3억
→ 저축 목표: 6.2억 😰

[DSR 시뮬레이션 후 (PRO 모드)]
대출 한도 (PRO): 4억 
→ 저축 목표: 5.2억 😊
→ 줄어든 금액: 1억 (reducedGap)
→ 경험치 보상: 500 (expGained)
```

---

## 📋 Phase 2 구현 범위

| # | 기능 | 설명 |
|---|------|------|
| 1 | **gameUpdate 연동** | DSR 시뮬레이션 응답에 `reducedGap`, `expGained` 추가 |
| 2 | **DSR 이력 관리** | 시뮬레이션 결과를 DB에 저장하여 이력 추적 |
| 3 | **gapAnalysis 연동** | 대시보드에 `virtualLoanLimit`, `requiredSavings` 필드 추가 |
| 4 | **dsrMode 상태 관리** | User의 DSR 모드(LITE/PRO) 저장 및 전환 |
| 5 | **프로필 업데이트** | PRO 시뮬레이션 입력값을 User 프로필에 저장 |
| 6 | **캐시 무효화 정책** | 목표/프로필 변경 시 캐시 리셋 |
| 7 | **온보딩 currentAssets 수집** | 온보딩 시 현재 자산 입력 |

---

## 📋 리뷰 피드백 반영 사항

| # | 피드백 | 조치 |
|---|--------|------|
| 1 | `requiredSavings` 영속화 누락 | ✅ `cached_max_loan_amount` 컬럼 추가 |
| 2 | 대시보드가 항상 LITE 한도 사용 | ✅ PRO 캐시 우선 (DSR 섹션 + Gap 섹션 모두) |
| 3 | PRO 입력을 프로필에 저장 안 함 | ✅ PRO 입력값을 User 프로필에 저장 |
| 4 | 갭 계산에 `currentAssets` 미포함 | ✅ 온보딩에서 수집 + 갭 계산에 포함 |
| 5 | GameUpdate 기준값 불명확 | ✅ 업데이트 직전 캐시값과 비교 |
| 6 | 경험치 영속화 누락 | ✅ `UserMapper.addExp()` 추가 |
| 7 | 캐시 무효화 정책 없음 | ✅ UserService에서 호출 (현재 코드 기반) |
| 8 | **온보딩에서 currentAssets 미수집** | ✅ OnboardingRequest + UserMapper 수정 |
| 9 | **Gap 계산 이중 차감 위험** | ✅ 공식 정리: `currentAssets`=초기자산, `currentSavedAmount`=이후 저축 (별개) |
| 10 | **목표 미설정 시 임시 목표** | ✅ 선호 지역 평균 시세로 gapAnalysis 제공 |
| 11 | **DSR 섹션도 PRO 우선** | ✅ Gap + DSR 모두 PRO 결과 사용 (이력 기반 복원) |

---

## 💡 Gap 계산 공식 정리

> [!IMPORTANT]
> **자산 용어 정의:**
> - `currentAssets`: 온보딩 시 입력한 **초기 자산** (User 테이블에 저장)
> - `currentSavedAmount`: 목표 설정 후 **저축한 금액** (DreamHome 테이블에 저장)
> - 두 값은 **별개**이므로 이중 차감이 아님
>
> **Spec 공식:** `requiredSavings = targetAmount - currentAssets - currentSavedAmount - maxLoanAmount`

---

## 구현 순서

```
1. 스키마 변경 (user 컬럼, dsr_calculation_history 테이블)
2. OnboardingRequest에 currentAssets 필드 추가
3. UserMapper.updateOnboarding에 currentAssets 추가
4. User.java 필드 추가
5. DsrCalculationHistory.java 도메인 생성
6. DsrHistoryMapper.java 생성
7. UserMapper.java 메서드 추가
8. DsrSimulationResponse.java에 GameUpdate 추가
9. DsrService.java 수정
10. DashboardResponse.java에 GapAnalysisSection 추가
11. DashboardService.java 수정 (PRO 결과 이력 기반 표시 + 목표 미설정 처리)
12. UserService.java에 캐시 무효화 추가
13. Swagger UI 테스트
```

---

## Step 1: 스키마 변경

### [MODIFY] schema-mysql.sql

```sql
-- ============================================================================
-- Phase 2: DSR 상태 관리
-- ============================================================================

-- User 테이블에 DSR 관련 컬럼 추가
ALTER TABLE `user` ADD COLUMN dsr_mode VARCHAR(10) DEFAULT 'LITE' COMMENT 'DSR 모드 (LITE/PRO)';
ALTER TABLE `user` ADD COLUMN last_dsr_calculation_at TIMESTAMP NULL COMMENT '마지막 DSR 계산 시각';
ALTER TABLE `user` ADD COLUMN cached_max_loan_amount BIGINT NULL COMMENT 'PRO 모드 대출 한도 캐시';
ALTER TABLE `user` ADD COLUMN current_assets BIGINT DEFAULT 0 COMMENT '온보딩 시 입력한 현재 자산';

-- DSR 계산 이력 테이블
CREATE TABLE dsr_calculation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    input_json TEXT NOT NULL COMMENT 'DsrInput JSON',
    result_json TEXT NOT NULL COMMENT 'DsrResult JSON',
    dsr_mode VARCHAR(10) NOT NULL COMMENT 'LITE/PRO',
    max_loan_amount BIGINT NOT NULL COMMENT '최대 대출 가능액',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    INDEX idx_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='DSR 계산 이력 테이블';
```

### [MODIFY] schema-h2.sql

```sql
-- Phase 2: DSR 상태 관리
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS dsr_mode VARCHAR(10) DEFAULT 'LITE';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS last_dsr_calculation_at TIMESTAMP NULL;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS cached_max_loan_amount BIGINT NULL;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS current_assets BIGINT DEFAULT 0;

CREATE TABLE IF NOT EXISTS dsr_calculation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    input_json CLOB NOT NULL,
    result_json CLOB NOT NULL,
    dsr_mode VARCHAR(10) NOT NULL,
    max_loan_amount BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_dsr_history_user ON dsr_calculation_history(user_id, created_at DESC);
```

---

## Step 2: OnboardingRequest에 currentAssets 추가

### [MODIFY] src/main/java/com/jipjung/project/controller/dto/request/OnboardingRequest.java

```diff
  @Schema(description = "월 기존 대출 상환액 (원 단위)", example = "500000")
  @NotNull(message = "월 기존 대출 상환액은 필수입니다")
  @Min(value = 0, message = "월 기존 대출 상환액은 0 이상이어야 합니다")
  Long existingLoanMonthly,

+ @Schema(description = "현재 보유 자산 (원 단위)", example = "30000000")
+ @NotNull(message = "현재 자산은 필수입니다")
+ @Min(value = 0, message = "현재 자산은 0 이상이어야 합니다")
+ Long currentAssets,

  @Schema(description = "선호 지역 배열")
  ...
```

- 컨트롤러 요청 예시/Swagger 예제에도 `currentAssets` 필드를 추가하여 클라이언트 혼선을 방지한다.

---

## Step 3: UserMapper.updateOnboarding 수정

### [MODIFY] src/main/java/com/jipjung/project/repository/UserMapper.java

```diff
  int updateOnboarding(
          @Param("userId") Long userId,
          @Param("birthYear") Integer birthYear,
          @Param("annualIncome") Long annualIncome,
-         @Param("existingLoanMonthly") Long existingLoanMonthly
+         @Param("existingLoanMonthly") Long existingLoanMonthly,
+         @Param("currentAssets") Long currentAssets
  );
```

### [MODIFY] src/main/resources/mapper/UserMapper.xml

```xml
<update id="updateOnboarding">
    UPDATE user
    SET birth_year = #{birthYear},
        annual_income = #{annualIncome},
        existing_loan_monthly = #{existingLoanMonthly},
        current_assets = #{currentAssets},
        onboarding_completed = true
    WHERE user_id = #{userId} AND is_deleted = false
</update>
```

---

## Step 4: User.java 필드 추가

### [MODIFY] src/main/java/com/jipjung/project/domain/User.java

```java
// 금융 정보
private Long annualIncome;
private Long existingLoanMonthly;
private Long currentAssets;              // 온보딩 시 입력한 현재 자산

// DSR 상태 (Phase 2)
private String dsrMode;                   // "LITE" or "PRO"
private LocalDateTime lastDsrCalculationAt;
private Long cachedMaxLoanAmount;         // PRO 모드 대출 한도 캐시
```

**UserMapper.xml에 매핑 추가:**

```xml
<result property="currentAssets" column="current_assets"/>
<result property="dsrMode" column="dsr_mode"/>
<result property="lastDsrCalculationAt" column="last_dsr_calculation_at"/>
<result property="cachedMaxLoanAmount" column="cached_max_loan_amount"/>
```

---

## Step 5: DsrCalculationHistory.java 생성

### [NEW] src/main/java/com/jipjung/project/domain/DsrCalculationHistory.java

```java
package com.jipjung.project.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DsrCalculationHistory {
    private Long id;
    private Long userId;
    private String inputJson;
    private String resultJson;
    private String dsrMode;
    private Long maxLoanAmount;
    private LocalDateTime createdAt;
}
```

---

## Step 6: DsrHistoryMapper.java 생성

### [NEW] src/main/java/com/jipjung/project/repository/DsrHistoryMapper.java

```java
package com.jipjung.project.repository;

import com.jipjung.project.domain.DsrCalculationHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DsrHistoryMapper {

    @Insert("""
        INSERT INTO dsr_calculation_history 
            (user_id, input_json, result_json, dsr_mode, max_loan_amount)
        VALUES 
            (#{userId}, #{inputJson}, #{resultJson}, #{dsrMode}, #{maxLoanAmount})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DsrCalculationHistory history);

    @Select("""
        SELECT * FROM dsr_calculation_history
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
    List<DsrCalculationHistory> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT * FROM dsr_calculation_history
        WHERE user_id = #{userId} AND dsr_mode = 'PRO'
        ORDER BY created_at DESC
        LIMIT 1
    """)
    DsrCalculationHistory findLatestProByUserId(@Param("userId") Long userId);
}
```

---

## Step 7: UserMapper.java 메서드 추가

### [MODIFY] src/main/java/com/jipjung/project/repository/UserMapper.java

```java
/**
 * DSR 캐시 업데이트 (PRO 시뮬레이션 후)
 */
@Update("""
    UPDATE user 
    SET dsr_mode = #{dsrMode}, 
        cached_max_loan_amount = #{cachedMaxLoanAmount},
        last_dsr_calculation_at = NOW() 
    WHERE user_id = #{userId}
""")
int updateDsrCache(
    @Param("userId") Long userId, 
    @Param("dsrMode") String dsrMode,
    @Param("cachedMaxLoanAmount") Long cachedMaxLoanAmount
);

/**
 * 경험치 추가
 */
@Update("UPDATE user SET current_exp = current_exp + #{expToAdd} WHERE user_id = #{userId}")
int addExp(@Param("userId") Long userId, @Param("expToAdd") int expToAdd);

/**
 * DSR 캐시 무효화 (목표/프로필 변경 시)
 */
@Update("UPDATE user SET cached_max_loan_amount = NULL, dsr_mode = 'LITE' WHERE user_id = #{userId}")
int invalidateDsrCache(@Param("userId") Long userId);

/**
 * PRO 시뮬레이션 입력값으로 프로필 업데이트
 */
@Update("""
    UPDATE user 
    SET annual_income = #{annualIncome}, existing_loan_monthly = #{existingLoanMonthly}
    WHERE user_id = #{userId}
""")
int updateFinancialInfo(@Param("userId") Long userId, @Param("annualIncome") Long annualIncome, @Param("existingLoanMonthly") Long existingLoanMonthly);
```

---

## Step 8: DsrSimulationResponse.java에 GameUpdate 추가

### [MODIFY] src/main/java/com/jipjung/project/controller/dto/response/DsrSimulationResponse.java

```java
// Spec 일치: reducedGap, expGained만 반환
@Schema(description = "게임 갱신 정보")
public record GameUpdate(
        @Schema(description = "줄어든 목표 저축액 (원)", example = "50000000")
        long reducedGap,

        @Schema(description = "획득 경험치", example = "500")
        int expGained
) {}
```

---

## Step 9: DsrService.java 수정

### [MODIFY] src/main/java/com/jipjung/project/service/DsrService.java

**핵심 로직:**

```java
public DsrSimulationResponse simulate(Long userId, DsrSimulationRequest request) {
    User user = findUserOrThrow(userId);
    
    // 1. DSR 계산
    DsrInput input = buildDsrInput(request, resolveAge(user));
    DsrResult result = dsrCalculator.calculateMaxLoan(input, DsrPolicy.bankDefault2025H2());
    
    // 2. 게임 갱신 계산 (캐시 덮어쓰기 전!)
    long oldMaxLoan = user.getCachedMaxLoanAmount() != null 
            ? user.getCachedMaxLoanAmount() 
            : calculateLiteDsr(user).maxLoanAmount();
    GameUpdate gameUpdate = calculateGameUpdate(user, oldMaxLoan, result.maxLoanAmount());
    
    // 3. 프로필 업데이트 (연소득, 월 상환액)
    userMapper.updateFinancialInfo(userId, request.annualIncome(), request.existingAnnualDebtService() / 12);
    
    // 4. DSR 캐시 업데이트
    userMapper.updateDsrCache(userId, "PRO", result.maxLoanAmount());
    
    // 5. 경험치 반영
    if (gameUpdate != null && gameUpdate.expGained() > 0) {
        userMapper.addExp(userId, gameUpdate.expGained());
    }
    
    // 6. 이력 저장
    saveHistory(userId, input, result, "PRO");

    return DsrSimulationResponse.from(result, stressRate, youthMultiplier, tip, gameUpdate);
}

/**
 * 게임 갱신 계산
 * - Spec 공식: requiredSavings = targetAmount - currentAssets - currentSavedAmount - maxLoanAmount
 */
private GameUpdate calculateGameUpdate(User user, long oldMaxLoan, long newMaxLoan) {
    DreamHome dreamHome = dreamHomeMapper.findActiveByUserId(user.getId());
    if (dreamHome == null) return null;

    long targetAmount = dreamHome.getTargetAmount() != null ? dreamHome.getTargetAmount() : 0L;
    long currentAssets = user.getCurrentAssets() != null ? user.getCurrentAssets() : 0L;
    long currentSaved = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0L;

    long oldRequired = Math.max(0, targetAmount - currentAssets - currentSaved - oldMaxLoan);
    long newRequired = Math.max(0, targetAmount - currentAssets - currentSaved - newMaxLoan);
    
    long reducedGap = Math.max(0, oldRequired - newRequired);
    int expGained = reducedGap > 0 ? Math.min(500, (int)(reducedGap / 10_000_000) * 100) : 0;

    return new GameUpdate(reducedGap, expGained);
}
```

---

## Step 10: DashboardResponse.java에 GapAnalysisSection 추가

### [MODIFY] src/main/java/com/jipjung/project/controller/dto/response/DashboardResponse.java

```java
@Schema(description = "갭 분석 섹션")
public record GapAnalysisSection(
        @Schema(description = "목표 설정 여부") boolean hasTarget,
        @Schema(description = "목표 금액 (미설정 시 지역 평균)") long targetAmount,
        @Schema(description = "현재 자산 (온보딩)") long currentAssets,
        @Schema(description = "현재 저축") long currentSavedAmount,
        @Schema(description = "추정 대출 한도") long virtualLoanLimit,
        @Schema(description = "필요 저축액") long requiredSavings,
        @Schema(description = "DSR 모드") String dsrMode
) {
    /**
     * 목표 설정 시
     */
    public static GapAnalysisSection from(DreamHome dreamHome, User user, long maxLoanAmount) {
        long targetAmount = dreamHome.getTargetAmount() != null ? dreamHome.getTargetAmount() : 0L;
        long currentAssets = user.getCurrentAssets() != null ? user.getCurrentAssets() : 0L;
        long currentSaved = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0L;
        long requiredSavings = Math.max(0, targetAmount - currentAssets - currentSaved - maxLoanAmount);
        
        return new GapAnalysisSection(
                true, targetAmount, currentAssets, currentSaved,
                maxLoanAmount, requiredSavings,
                user.getDsrMode() != null ? user.getDsrMode() : "LITE"
        );
    }
    
    /**
     * 목표 미설정 시 (임시 목표: 선호 지역 평균 시세)
     */
    public static GapAnalysisSection forNoTarget(User user, long maxLoanAmount, long regionAvgPrice) {
        long currentAssets = user.getCurrentAssets() != null ? user.getCurrentAssets() : 0L;
        long requiredSavings = Math.max(0, regionAvgPrice - currentAssets - maxLoanAmount);
        
        return new GapAnalysisSection(
                false, regionAvgPrice, currentAssets, 0L,
                maxLoanAmount, requiredSavings,
                user.getDsrMode() != null ? user.getDsrMode() : "LITE"
        );
    }
}
```

---

## Step 11: DashboardService.java 수정

### [MODIFY] src/main/java/com/jipjung/project/service/DashboardService.java

**핵심 변경: PRO 결과 우선 사용 + 목표 미설정 처리**

```java
public DashboardResponse getDashboard(Long userId) {
    // ... 기존 코드 ...

    // 8. DSR 계산 - PRO 결과 우선
    DsrResult dsrResult;
    long maxLoanAmount;
    long recognizedAnnualIncome;  // DsrSection 표시용

    if (user.getCachedMaxLoanAmount() != null) {
        // PRO 결과: 이력 테이블에서 최신 PRO JSON 복원
        DsrCalculationHistory latestPro = dsrHistoryMapper.findLatestProByUserId(userId);
        if (latestPro != null) {
            dsrResult = objectMapper.readValue(latestPro.getResultJson(), DsrResult.class);
            DsrInput input = objectMapper.readValue(latestPro.getInputJson(), DsrInput.class);

            // PRO 입력 기반으로 인정소득 재계산 (나이 계산 포함)
            int age = resolveAge(user);
            DsrPolicy policy = DsrPolicy.bankDefault2025H2();
            recognizedAnnualIncome = Math.round(input.annualIncome() * policy.getYouthIncomeMultiplier(age));
            maxLoanAmount = dsrResult.maxLoanAmount();
        } else {
            // 캐시만 있을 때는 캐시값으로 대체, 퍼센트 등은 LITE 계산 사용
            DsrService.LiteDsrSnapshot snapshot = dsrService.calculateLiteDsrSnapshot(user);
            dsrResult = snapshot.result();
            maxLoanAmount = user.getCachedMaxLoanAmount();
            recognizedAnnualIncome = snapshot.recognizedAnnualIncome();
        }
    } else {
        // LITE 계산
        DsrService.LiteDsrSnapshot snapshot = dsrService.calculateLiteDsrSnapshot(user);
        dsrResult = snapshot.result();
        maxLoanAmount = dsrResult.maxLoanAmount();
        recognizedAnnualIncome = snapshot.recognizedAnnualIncome();
    }

    DsrSection dsrSection = DsrSection.from(user, dsrResult, recognizedAnnualIncome);

    // 9. Gap Analysis 계산
    GapAnalysisSection gapAnalysis;
    if (dreamHome != null) {
        // 목표 설정됨
        gapAnalysis = GapAnalysisSection.from(dreamHome, user, maxLoanAmount);
    } else {
        // 목표 미설정 → 선호 지역 평균 시세로 임시 목표
        long regionAvgPrice = getRegionAveragePrice(userId);
        gapAnalysis = GapAnalysisSection.forNoTarget(user, maxLoanAmount, regionAvgPrice);
    }

    return DashboardResponse.from(..., dsrSection, gapAnalysis);
}

/**
 * 선호 지역 평균 시세 조회
 * - UserPreferredArea 테이블에서 첫 번째 지역
 * - 해당 지역의 최근 거래 평균가
 * - 실패 시 기본값(예: 9.5억) 반환
 *
 * (예외/조회 실패 시 LITE 계산으로 graceful degrade)
 */
private long getRegionAveragePrice(Long userId) {
    // 1) UserPreferredArea에서 첫 지역 조회
    // 2) ApartmentDealMapper로 해당 구/동의 최근 거래 평균가 조회
    // 3) 실패 시 기본값(예: 9.5억) 반환
}

// resolveAge(user)는 DsrService의 로직과 동일하게 (올해 - birthYear) 사용
```

- 추가 의존성/DI: `DsrHistoryMapper`, `ObjectMapper`를 주입하고, `JsonProcessingException` 등 발생 시 LITE 계산으로 폴백하여 응답은 지속 제공.

---

## Step 12: UserService.java에 캐시 무효화 추가

### [MODIFY] src/main/java/com/jipjung/project/service/UserService.java

**updateProfile() 수정:**

```java
@Transactional
public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
    User user = findUserOrThrow(userId);

    // 연소득 또는 부채가 변경된 경우 DSR 캐시 무효화
    boolean incomeChanged = !Objects.equals(user.getAnnualIncome(), request.annualIncome());
    boolean debtChanged = !Objects.equals(user.getExistingLoanMonthly(), request.existingLoanMonthly());
    
    if (incomeChanged || debtChanged) {
        userMapper.invalidateDsrCache(userId);
        log.info("DSR cache invalidated due to profile change. userId: {}", userId);
    }

    // 기존 업데이트 로직...
    int updatedRows = userMapper.updateProfile(...);
    // ...
}
```

**saveOnboarding() 수정:**

```java
@Transactional
public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
    // 1. 사용자 존재 여부 확인
    User user = findUserOrThrow(userId);

    // 2. 온보딩 정보 업데이트 (currentAssets 추가!)
    int updatedRows = userMapper.updateOnboarding(
            userId,
            request.birthYear(),
            request.annualIncome(),
            request.existingLoanMonthly(),
            request.currentAssets()  // 추가
    );
    // ... 나머지 동일
}
```

**목표 변경/삭제 시 무효화:**

- 현재 코드베이스에는 DreamHomeService가 없으므로, 목표 설정/변경/삭제를 처리하는 로직(컨트롤러 또는 매퍼 레벨)에 `userMapper.invalidateDsrCache(userId);` 호출을 추가한다.

---

## Verification Plan

### 빌드 테스트

```bash
./mvnw clean compile
```

### Swagger UI 테스트

**테스트 케이스 1: 온보딩 currentAssets 저장**

- `POST /api/users/onboarding` 에 `currentAssets` 포함
- DB에서 `SELECT current_assets FROM user WHERE user_id = ?` 확인

**테스트 케이스 2: PRO 시뮬레이션 + GameUpdate**

- 목표 설정된 사용자로 `POST /api/simulation/dsr`
- `gameUpdate.reducedGap`, `expGained` 확인
- DB에서 `current_exp` 증가 확인

**테스트 케이스 3: 대시보드 PRO 우선 사용**

- PRO 시뮬레이션 후 `GET /api/users/dashboard`
- `gapAnalysis.dsrMode = "PRO"` 확인
- `gapAnalysis.virtualLoanLimit` = PRO 결과값 확인

**테스트 케이스 4: 목표 미설정 시 임시 목표**

- 목표(DreamHome) 없는 사용자로 `GET /api/users/dashboard`
- `gapAnalysis.hasTarget = false` 확인
- `gapAnalysis.targetAmount` = 선호 지역 평균 시세 확인

**테스트 케이스 5: 캐시 무효화**

- PRO 시뮬레이션 후 프로필에서 연소득 변경
- 대시보드에서 `gapAnalysis.dsrMode = "LITE"` 확인

---

## 파일 변경 요약

| 작업 | 경로 | 변경 내용 |
|------|------|----------|
| **[MODIFY]** | `schema-mysql.sql`, `schema-h2.sql` | `cached_max_loan_amount`, `current_assets` 컬럼 |
| **[MODIFY]** | `dto/request/OnboardingRequest.java` | `currentAssets` 필드 추가 |
| **[MODIFY]** | `repository/UserMapper.java` | `updateOnboarding`에 `currentAssets` 추가 |
| **[MODIFY]** | `resources/mapper/UserMapper.xml` | 온보딩 쿼리 수정 |
| **[MODIFY]** | `domain/User.java` | `currentAssets`, `cachedMaxLoanAmount` 필드 |
| **[NEW]** | `domain/DsrCalculationHistory.java` | 이력 도메인 |
| **[NEW]** | `repository/DsrHistoryMapper.java` | 이력 Mapper |
| **[MODIFY]** | `repository/UserMapper.java` | `updateDsrCache`, `addExp`, `invalidateDsrCache` |
| **[MODIFY]** | `dto/response/DsrSimulationResponse.java` | `GameUpdate` 추가 |
| **[MODIFY]** | `service/DsrService.java` | 프로필 업데이트 + 캐시 + 경험치 |
| **[MODIFY]** | `dto/response/DashboardResponse.java` | `GapAnalysisSection` (hasTarget 포함) |
| **[MODIFY]** | `service/DashboardService.java` | PRO 우선 + 목표 미설정 처리 |
| **[MODIFY]** | `service/UserService.java` | 온보딩 currentAssets + 캐시 무효화 |
