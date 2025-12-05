# Dashboard API 구현 계획서 (v4 - Final)

`GET /api/users/dashboard` - 대시보드 통합 데이터 조회 API

---

## 📋 변경 이력

| 버전 | 변경 내용 |
|-----|----------|
| v4 | 자산 윈도우 기준, Streak 전제조건, 유저 상태 정책, 테마 fallback 로깅 |
| v3 | Assets 계산 수정, 테마 fallback, DSR null 처리, Service 플로우 상세화 |
| v2 | 데이터 소스 정의, DTO 패턴, Soft Delete, 집계 쿼리, Null 처리 |

---

## 1. 핵심 정책 결정

### 1.1 Source of Truth

| 항목 | 정책 |
|-----|------|
| 저축 총액 | `dream_home.current_saved_amount` 단일 소스 |
| 시간대 | `ZoneId.of("Asia/Seoul")` 고정 |
| 기본 테마 | `DEFAULT_THEME_ID = 1` |
| 기본 레벨 | `DEFAULT_LEVEL = 1`, `DEFAULT_REQUIRED_EXP = 100` |

### 1.2 유저 상태 정책

| 조건 | 처리 |
|-----|------|
| `is_deleted = true` | `ResourceNotFoundException(USER_NOT_FOUND)` |
| `is_active = false` | 현재 user 테이블에 없음 → 향후 추가 시 동일 정책 |

### 1.3 DSR Null/Zero 처리

| 조건 | dsrPercent | gradeLabel | gradeColor |
|-----|-----------|------------|------------|
| `annualIncome == null OR 0` | 0.0 | "소득 정보 없음" | "GRAY" |
| `existingLoanMonthly == null` | 0.0 | "매우 안전" | "GREEN" |
| 정상 계산 | `(loan / monthlyIncome) * 100` | 구간별 | 구간별 |

### 1.4 Streak/보상 정책

| 조건 | isTodayParticipated | rewardAvailable |
|-----|-------------------|-----------------|
| 오늘 미참여 | false | true |
| 오늘 참여 (저축 완료) | true | false |

> **전제조건**: 저축 API에서 저축 성공 시 **streak_history에 오늘 날짜 레코드 필수 생성**

### 1.5 테마 Fallback 체인

```
1. user.selectedThemeId로 조회 → 실패 시 log.warn
2. DEFAULT_THEME_ID(1)로 재조회 → 실패 시 log.error
3. 기본 이미지 "/assets/house/default.png"
```

---

## 2. 데이터 소스 정의 (필드별)

### 2.1 ProfileSection

| 필드 | 소스 | 기본값 |
|-----|------|-------|
| `nickname` | `user.nickname` | - |
| `title` | `growth_level.step_name` + " 건축가" | "신입 건축가" |
| `statusMessage` | 하드코딩 | "목표를 향해 천천히, 꾸준히 가고 있어요" |
| `level` | `user.current_level` | 1 |
| `levelProgress.currentExp` | `user.current_exp` | 0 |
| `levelProgress.targetExp` | `growth_level.required_exp` | 100 |
| `levelProgress.percent` | `(currentExp / targetExp) * 100` | 0.0 |
| `levelProgress.remainingExp` | `targetExp - currentExp` | 100 |

### 2.2 GoalSection

| 필드 | 소스 | 기본값 (드림홈 없음) |
|-----|------|-------------------|
| `targetPropertyName` | `apartment.apt_nm` (JOIN) | "목표를 설정해주세요" |
| `totalAmount` | `dream_home.target_amount` | 0 |
| `savedAmount` | `dream_home.current_saved_amount` | 0 |
| `remainingAmount` | `MAX(0, total - saved)` | 0 |
| `achievementRate` | `(saved / total) * 100` | 0.0 |
| `isCompleted` | `saved >= total` | false |

### 2.3 StreakSection

| 필드 | 소스 | 기본값 |
|-----|------|-------|
| `currentStreak` | `user.streak_count` | 0 |
| `maxStreak` | `user.max_streak` | 0 |
| `isTodayParticipated` | `streak_history` 오늘 존재 여부 | false |
| `rewardAvailable` | `!isTodayParticipated` | true |
| `weeklyStatus` | 이번 주 월~일 조회 | 전체 achieved=false |

### 2.4 DsrSection

| 필드 | 소스 |
|-----|------|
| `dsrPercent` | `(existingLoanMonthly / monthlyIncome) * 100` |
| `financialInfo.monthlyIncome` | `annualIncome / 12` |
| `financialInfo.existingLoanRepayment` | `user.existing_loan_monthly` |
| `financialInfo.availableCapacity` | `MAX(0, monthlyIncome * 0.4 - loan)` |

### 2.5 AssetsSection (v4 수정)

| 필드 | 계산 방식 | 기본값 |
|-----|----------|-------|
| `totalAsset` | `dream_home.current_saved_amount` | 0 |
| `chartData` | **최근 30일** + windowStartBalance | `[]` |
| `growthAmount` | `totalAsset - windowStartBalance` | 0 |
| `growthRate` | `windowStartBalance > 0 ? (growthAmount / start) * 100 : 0` | 0.0 |

**출금 처리**: `DEPOSIT → +amount`, `WITHDRAW → -amount`
**윈도우 시작 잔액**: `windowStartBalance = (윈도우 시작일 이전까지의 모든 거래 합계)`, 차트의 첫 포인트는 이 시작 잔액으로 넣어 단절 없이 이어지도록 함

### 2.6 ShowroomSection

| 필드 | 소스 | 기본값 |
|-----|------|-------|
| `currentStep` | `user.current_level` | 1 |
| `totalSteps` | `COUNT(*) FROM growth_level` | 7 |
| `stepTitle` | `growth_level.step_name` | "터파기" |
| `stepDescription` | `growth_level.description` | "기초 공사를 시작합니다" |
| `imageUrl` | `theme_asset.image_url` | "/assets/house/default.png" |

---

## 3. DTO 설계 (기존 패턴: record + from 팩토리)

```java
public record DashboardResponse(
    ProfileSection profile,
    GoalSection goal,
    StreakSection streak,
    DsrSection dsr,
    AssetsSection assets,
    ShowroomSection showroom
) {
    public static DashboardResponse from(
            User user,
            GrowthLevel level,          // nullable
            DreamHome dreamHome,        // nullable
            List<StreakHistory> weeklyStreaks,
            boolean todayParticipated,
            AssetsData assetsData,
            ThemeAsset themeAsset,      // nullable
            int totalSteps
    ) { ... }
}
```

---

## 4. Service 플로우 (v4 상세화)

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_THEME_ID = 1;
    private static final int DEFAULT_LEVEL = 1;
    private static final int CHART_WINDOW_DAYS = 30;

    public DashboardResponse getDashboard(Long userId) {
        // 1. User (is_deleted=false, 없으면 예외)
        User user = Optional.ofNullable(userMapper.findById(userId))
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 2. GrowthLevel (없으면 기본값)
        int userLevel = user.getCurrentLevel() != null ? user.getCurrentLevel() : DEFAULT_LEVEL;
        GrowthLevel level = growthLevelMapper.findByLevel(userLevel);

        // 3. totalSteps
        int totalSteps = growthLevelMapper.countAll();
        if (totalSteps == 0) totalSteps = 7;

        // 4. DreamHome (없으면 null)
        DreamHome dreamHome = dreamHomeMapper.findActiveByUserId(userId);

        // 5. ThemeAsset (fallback + 로깅)
        ThemeAsset themeAsset = resolveThemeAsset(user.getSelectedThemeId(), userLevel);

        // 6. Streak
        LocalDate today = LocalDate.now(ZONE_KST);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<StreakHistory> weekly = streakHistoryMapper.findByUserIdAndWeek(userId, weekStart, weekEnd);
        boolean todayParticipated = streakHistoryMapper.existsByUserIdAndDate(userId, today);

        // 7. Assets (윈도우 기반)
        AssetsData assetsData = buildAssetsData(dreamHome, today);

        // 8. 응답 생성
        return DashboardResponse.from(user, level, dreamHome, weekly, 
                                      todayParticipated, assetsData, themeAsset, totalSteps);
    }

    private ThemeAsset resolveThemeAsset(Integer selectedThemeId, int level) {
        if (selectedThemeId != null) {
            ThemeAsset asset = themeAssetMapper.findByThemeAndLevel(selectedThemeId, level);
            if (asset != null) return asset;
            log.warn("Theme {} not found for level {}", selectedThemeId, level);
        }
        ThemeAsset fallback = themeAssetMapper.findByThemeAndLevel(DEFAULT_THEME_ID, level);
        if (fallback == null) {
            log.error("Default theme asset not found for level {}", level);
        }
        return fallback;
    }

    private AssetsData buildAssetsData(DreamHome dreamHome, LocalDate today) {
        if (dreamHome == null) return new AssetsData(0, 0, 0.0, List.of());
        
        LocalDate windowStart = today.minusDays(CHART_WINDOW_DAYS);
        Long windowStartBalance = Optional.ofNullable(
            savingsHistoryMapper.sumBeforeDate(dreamHome.getDreamHomeId(), windowStart)
        ).orElse(0L);

        List<SavingsHistory> txs = savingsHistoryMapper
            .findByDreamHomeIdAndDateRange(dreamHome.getDreamHomeId(), windowStart, today);
        
        // buildChartData: 첫 포인트 = windowStartBalance, 이후 DEPOSIT(+)/WITHDRAW(-) 누적
        List<ChartData> chartData = buildChartData(windowStart, windowStartBalance, txs);
        
        long totalAsset = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0;
        long growthAmount = totalAsset - windowStartBalance;
        double growthRate = windowStartBalance > 0 
            ? Math.round((growthAmount * 1000.0 / windowStartBalance)) / 10.0 : 0.0;
        
        return new AssetsData(totalAsset, growthAmount, growthRate, chartData);
    }
}
```

---

## 5. Repository (Mapper)

### 5.1 인터페이스 + 메서드

| Mapper | 메서드 |
|--------|-------|
| `UserMapper` | findById (is_deleted=false) |
| `GrowthLevelMapper` | findByLevel, countAll |
| `ThemeAssetMapper` | findByThemeAndLevel (is_active, is_deleted 필터) |
| `DreamHomeMapper` | findActiveByUserId (status=ACTIVE, is_deleted=false) |
| `SavingsHistoryMapper` | sumBeforeDate, findByDreamHomeIdAndDateRange |
| `StreakHistoryMapper` | findByUserIdAndWeek, existsByUserIdAndDate |

### 5.2 추가 XML 쿼리

```xml
<!-- UserMapper.xml -->
<select id="findById" resultMap="UserResultMap">
    SELECT * FROM `user` 
    WHERE user_id = #{userId} AND is_deleted = false
</select>

<!-- SavingsHistoryMapper.xml -->
<select id="sumBeforeDate" resultType="java.lang.Long">
    SELECT COALESCE(SUM(
        CASE WHEN save_type = 'DEPOSIT' THEN amount ELSE -amount END
    ), 0)
    FROM savings_history
    WHERE dream_home_id = #{dreamHomeId} 
      AND is_deleted = false
      AND DATE(created_at) < #{date}
</select>

<select id="findByDreamHomeIdAndDateRange" resultMap="SavingsHistoryResultMap">
    SELECT * FROM savings_history
    WHERE dream_home_id = #{dreamHomeId}
      AND is_deleted = false
      AND DATE(created_at) BETWEEN #{startDate} AND #{endDate}
    ORDER BY created_at ASC
</select>
```

---

## 6. 파일 목록

### Schema/Data
| 파일 | 작업 | 상태 |
|-----|------|-----|
| `schema-h2.sql` | 테이블 추가 | ✅ 완료 |
| `data-h2.sql` | 테스트 데이터 | ⏸️ 대기 |

### Domain (8개)
| 파일 | 작업 |
|-----|------|
| `User.java` | 필드 추가 |
| `GrowthLevel.java` | 신규 |
| `HouseTheme.java` | 신규 |
| `ThemeAsset.java` | 신규 |
| `DreamHome.java` | 신규 |
| `SavingsHistory.java` | 신규 |
| `StreakHistory.java` | 신규 |
| `UserCollection.java` | 신규 |

### Repository (6개)
| 파일 | 메서드 |
|-----|-------|
| `UserMapper` | + findById |
| `GrowthLevelMapper` | findByLevel, countAll |
| `ThemeAssetMapper` | findByThemeAndLevel |
| `DreamHomeMapper` | findActiveByUserId |
| `SavingsHistoryMapper` | sumBeforeDate, findByDreamHomeIdAndDateRange |
| `StreakHistoryMapper` | findByUserIdAndWeek, existsByUserIdAndDate |

### DTO/Service/Controller
| 파일 | 비고 |
|-----|------|
| `DashboardResponse.java` | 중첩 record + from() |
| `DashboardService.java` | 플로우 상세화 |
| `DashboardController.java` | userDetails.getId() |

---

## 7. 검증 체크리스트

- [ ] User is_deleted=true → 404
- [ ] DreamHome null → goal/assets 기본값
- [ ] annualIncome=0 → DSR "소득 정보 없음", GRAY
- [ ] selectedThemeId 비활성 → fallback + log.warn
- [ ] DEFAULT_THEME도 없음 → 기본 이미지 + log.error
- [ ] chartData 윈도우 시작 잔액 포함
- [ ] WITHDRAW → -amount
- [ ] 저축 시 streak_history 생성 (저축 API 전제)
- [ ] 시간대 KST 고정
