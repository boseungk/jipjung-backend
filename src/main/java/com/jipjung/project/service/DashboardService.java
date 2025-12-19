package com.jipjung.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.controller.dto.response.DashboardResponse;
import com.jipjung.project.controller.dto.response.DashboardResponse.AssetsData;
import com.jipjung.project.controller.dto.response.DashboardResponse.ChartData;
import com.jipjung.project.controller.dto.response.DashboardResponse.DsrSection;
import com.jipjung.project.controller.dto.response.DashboardResponse.GapAnalysisSection;
import com.jipjung.project.domain.*;
import com.jipjung.project.dsr.DsrInput;
import com.jipjung.project.dsr.DsrPolicy;
import com.jipjung.project.dsr.DsrResult;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 서비스
 * - 대시보드 통합 데이터 조회 로직
 * - Phase 2: PRO 결과 우선 사용, GapAnalysis 통합
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_THEME_ID = 1;
    private static final int DEFAULT_LEVEL = 1;
    private static final int CHART_WINDOW_DAYS = 30;
    private static final int DEFAULT_TOTAL_STEPS = 7;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final AssetsData EMPTY_ASSETS = new AssetsData(0, 0, 0.0, List.of());

    /** 목표 미설정 시 기본 시세 (9.5억) */
    private static final long DEFAULT_REGION_AVG_PRICE = 950_000_000L;

    private final UserMapper userMapper;
    private final GrowthLevelMapper growthLevelMapper;
    private final HouseThemeMapper houseThemeMapper;
    private final DreamHomeMapper dreamHomeMapper;
    private final SavingsHistoryMapper savingsHistoryMapper;
    private final StreakHistoryMapper streakHistoryMapper;
    private final DsrHistoryMapper dsrHistoryMapper;
    private final UserPreferredAreaMapper userPreferredAreaMapper;
    private final ApartmentDealMapper apartmentDealMapper;
    private final DsrService dsrService;
    private final StreakService streakService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DashboardService(
            UserMapper userMapper,
            GrowthLevelMapper growthLevelMapper,
            HouseThemeMapper houseThemeMapper,
            DreamHomeMapper dreamHomeMapper,
            SavingsHistoryMapper savingsHistoryMapper,
            StreakHistoryMapper streakHistoryMapper,
            DsrHistoryMapper dsrHistoryMapper,
            UserPreferredAreaMapper userPreferredAreaMapper,
            ApartmentDealMapper apartmentDealMapper,
            DsrService dsrService,
            StreakService streakService,
            ObjectMapper objectMapper
    ) {
        this(
                userMapper,
                growthLevelMapper,
                houseThemeMapper,
                dreamHomeMapper,
                savingsHistoryMapper,
                streakHistoryMapper,
                dsrHistoryMapper,
                userPreferredAreaMapper,
                apartmentDealMapper,
                dsrService,
                streakService,
                objectMapper,
                Clock.system(ZONE_KST)
        );
    }

    public DashboardService(
            UserMapper userMapper,
            GrowthLevelMapper growthLevelMapper,
            HouseThemeMapper houseThemeMapper,
            DreamHomeMapper dreamHomeMapper,
            SavingsHistoryMapper savingsHistoryMapper,
            StreakHistoryMapper streakHistoryMapper,
            DsrHistoryMapper dsrHistoryMapper,
            UserPreferredAreaMapper userPreferredAreaMapper,
            ApartmentDealMapper apartmentDealMapper,
            DsrService dsrService,
            StreakService streakService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.growthLevelMapper = growthLevelMapper;
        this.houseThemeMapper = houseThemeMapper;
        this.dreamHomeMapper = dreamHomeMapper;
        this.savingsHistoryMapper = savingsHistoryMapper;
        this.streakHistoryMapper = streakHistoryMapper;
        this.dsrHistoryMapper = dsrHistoryMapper;
        this.userPreferredAreaMapper = userPreferredAreaMapper;
        this.apartmentDealMapper = apartmentDealMapper;
        this.dsrService = dsrService;
        this.streakService = streakService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 대시보드 통합 데이터 조회
     * <p>
     * 대시보드 접속 시 활동 기반 스트릭에 참여하므로 쓰기 트랜잭션이 필요합니다.
     *
     * @param userId 사용자 ID
     * @return 대시보드 응답 DTO
     * @throws ResourceNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = false)
    public DashboardResponse getDashboard(Long userId) {
        // 1. User 조회 (is_deleted=false, 없으면 예외)
        User user = findUserOrThrow(userId);

        // 2. 선호 지역 조회 (구/군)
        List<String> preferredAreas = loadPreferredAreas(userId);

        // 3. GrowthLevel 조회 (없으면 기본 레벨로 재조회)
        ResolvedLevel resolvedLevel = resolveGrowthLevel(resolveUserLevel(user));
        int userLevel = resolvedLevel.level();
        GrowthLevel level = resolvedLevel.growthLevel();

        // 4. totalSteps 조회
        int totalSteps = resolveTotalSteps();

        // 5. DreamHome 조회 (ACTIVE 우선, 없으면 최근 COMPLETED; 없으면 null)
        DreamHome dreamHome = dreamHomeMapper.findLatestForDashboardByUserId(userId);

        // 6. HouseTheme 조회 (fallback + 로깅)
        HouseTheme houseTheme = resolveHouseTheme(user.getSelectedThemeId());

        // 7. Streak 조회
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<StreakHistory> weeklyStreaks = streakHistoryMapper.findByUserIdAndWeek(userId, weekStart, weekEnd);
        boolean todayParticipated = streakHistoryMapper.existsByUserIdAndDate(userId, today);

        // 8. Assets 데이터 구축 (윈도우 기반)
        AssetsData assetsData = buildAssetsData(dreamHome, today);

        // 9. DSR 계산 - PRO 결과 우선 (Phase 2)
        DsrCalculationContext dsrContext = resolveDsrContext(userId, user);
        DsrSection dsrSection = DsrSection.from(user, dsrContext.dsrResult(), dsrContext.recognizedAnnualIncome());

        // 10. Gap Analysis 계산 (Phase 2)
        GapAnalysisSection gapAnalysis = buildGapAnalysis(userId, user, dreamHome, dsrContext.maxLoanAmount(), preferredAreas);

        // 11. 대시보드 접속 스트릭 참여 (1일 1회)
        try {
            streakService.participate(userId, ActivityType.DASHBOARD);
        } catch (Exception e) {
            log.warn("Dashboard streak participation failed for userId: {}", userId, e);
            // 실패해도 대시보드 조회는 정상 진행
        }

        // 12. 응답 생성
        return DashboardResponse.from(
                user, level, dreamHome, weeklyStreaks,
                todayParticipated, assetsData, houseTheme, totalSteps, dsrSection, gapAnalysis, preferredAreas
        );
    }

    // ==========================================================================
    // Phase 2: DSR & Gap Analysis
    // ==========================================================================

    /**
     * DSR 컨텍스트 결정 (PRO 우선)
     */
    private DsrCalculationContext resolveDsrContext(Long userId, User user) {
        DsrCalculationHistory latestPro = dsrHistoryMapper.findLatestProByUserId(userId);
        if (latestPro != null) {
            try {
                DsrResult dsrResult = objectMapper.readValue(latestPro.getResultJson(), DsrResult.class);
                DsrInput dsrInput = objectMapper.readValue(latestPro.getInputJson(), DsrInput.class);

                // PRO 입력 기반으로 인정소득 재계산
                int ageAtSimulation = dsrInput.age();
                DsrPolicy policy = DsrPolicy.bankDefault2025H2();
                long recognizedAnnualIncome = Math.round(dsrInput.annualIncome() * policy.getYouthIncomeMultiplier(ageAtSimulation));

                return new DsrCalculationContext(dsrResult, latestPro.getMaxLoanAmount(), recognizedAnnualIncome);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse PRO DSR history. Falling back to LITE and invalidating stale cache. userId: {}", userId, e);
                invalidateDsrCacheIfPresent(userId, user);
            }
        } else if (user.getCachedMaxLoanAmount() != null) {
            log.info("Cached PRO max loan exists without PRO history. Invalidating and recalculating with LITE. userId: {}", userId);
            invalidateDsrCacheIfPresent(userId, user);
        }

        // LITE 계산 (PRO 이력 부재 혹은 파싱 실패 시)
        DsrService.LiteDsrSnapshot snapshot = dsrService.calculateLiteDsrSnapshot(user);
        return new DsrCalculationContext(snapshot.result(), snapshot.result().maxLoanAmount(), snapshot.recognizedAnnualIncome());
    }

    /**
     * Gap Analysis 구축
     */
    private GapAnalysisSection buildGapAnalysis(Long userId, User user, DreamHome dreamHome, long maxLoanAmount, List<String> preferredAreas) {
        if (dreamHome != null) {
            // 목표 설정됨
            return GapAnalysisSection.from(dreamHome, user, maxLoanAmount);
        } else {
            // 목표 미설정 → 선호 지역 평균 시세로 임시 목표
            long regionAvgPrice = getRegionAveragePrice(userId, preferredAreas);
            return GapAnalysisSection.forNoTarget(user, maxLoanAmount, regionAvgPrice);
        }
    }

    /**
     * 선호 지역 평균 시세 조회
     * - UserPreferredArea 테이블에서 첫 번째 지역
     * - 해당 지역의 최근 거래 평균가
     * - 실패 시 기본값(9.5억) 반환
     */
    private long getRegionAveragePrice(Long userId, List<String> preferredAreas) {
        try {
            if (preferredAreas == null || preferredAreas.isEmpty()) {
                return DEFAULT_REGION_AVG_PRICE;
            }

            String firstArea = normalizeToGugun(preferredAreas.get(0));
            Long avgPrice = apartmentDealMapper.findAverageRecentDealAmountByGugun(firstArea);
            if (avgPrice != null && avgPrice > 0) {
                return avgPrice;
            }
        } catch (DataAccessException e) {
            log.warn("Failed to get region average price from DB. userId: {}. Using default.", userId, e);
        } catch (RuntimeException e) {
            log.warn("Unexpected error when getting region average price. userId: {}. Using default.", userId, e);
        }
        return DEFAULT_REGION_AVG_PRICE;
    }

    private void invalidateDsrCacheIfPresent(Long userId, User user) {
        if (user.getCachedMaxLoanAmount() == null) {
            return;
        }
        try {
            int updated = userMapper.invalidateDsrCache(userId);
            log.info("Invalidated stale DSR cache for user {} (rows={})", userId, updated);
        } catch (DataAccessException e) {
            log.warn("Failed to invalidate DSR cache for user {}.", userId, e);
        }
    }

    private record DsrCalculationContext(DsrResult dsrResult, long maxLoanAmount, long recognizedAnnualIncome) {}

    // ==========================================================================
    // Private Helper Methods
    // ==========================================================================

    private User findUserOrThrow(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private List<String> loadPreferredAreas(Long userId) {
        try {
            List<String> preferredAreas = userPreferredAreaMapper.findByUserId(userId);
            if (preferredAreas == null || preferredAreas.isEmpty()) {
                return List.of();
            }

            return preferredAreas.stream()
                    .map(this::normalizeToGugun)
                    .filter(area -> area != null && !area.isEmpty())
                    .distinct()
                    .toList();
        } catch (DataAccessException e) {
            log.warn("Failed to load preferred areas for user {}. Returning empty list.", userId, e);
            return List.of();
        }
    }

    private int resolveUserLevel(User user) {
        return user.getCurrentLevel() != null ? user.getCurrentLevel() : DEFAULT_LEVEL;
    }

    private int resolveTotalSteps() {
        int count = growthLevelMapper.countAll();
        return count > 0 ? count : DEFAULT_TOTAL_STEPS;
    }

    private HouseTheme resolveHouseTheme(Integer selectedThemeId) {
        if (selectedThemeId != null) {
            HouseTheme theme = houseThemeMapper.findById(selectedThemeId);
            if (theme != null && Boolean.TRUE.equals(theme.getIsActive())) {
                return theme;
            }
            log.warn("Theme {} not found or inactive. Falling back to default theme.", selectedThemeId);
        }

        HouseTheme fallback = houseThemeMapper.findById(DEFAULT_THEME_ID);
        if (fallback != null && Boolean.TRUE.equals(fallback.getIsActive())) {
            return fallback;
        }
        if (fallback != null) {
            log.warn("Default theme {} is inactive. Using built-in default.", DEFAULT_THEME_ID);
        }

        log.error("Default theme (id={}) not found. Using built-in default.", DEFAULT_THEME_ID);
        return HouseTheme.builder()
                .themeId(DEFAULT_THEME_ID)
                .themeCode("MODERN")
                .themeName("모던 하우스")
                .imagePath(null)  // getFullImageUrl()에서 폴백 처리
                .isActive(true)
                .build();
    }

    private ResolvedLevel resolveGrowthLevel(int requestedLevel) {
        GrowthLevel level = growthLevelMapper.findByLevel(requestedLevel);
        if (level != null || requestedLevel == DEFAULT_LEVEL) {
            return new ResolvedLevel(requestedLevel, level);
        }

        log.warn("Growth level {} not found. Falling back to default level {}.", requestedLevel, DEFAULT_LEVEL);
        GrowthLevel fallback = growthLevelMapper.findByLevel(DEFAULT_LEVEL);
        if (fallback == null) {
            log.error("Default growth level {} not found.", DEFAULT_LEVEL);
            throw new IllegalStateException("Default growth level not configured: " + DEFAULT_LEVEL);
        }
        return new ResolvedLevel(DEFAULT_LEVEL, fallback);
    }

    private AssetsData buildAssetsData(DreamHome dreamHome, LocalDate today) {
        if (dreamHome == null || dreamHome.getDreamHomeId() == null) {
            return EMPTY_ASSETS;
        }

        Long dreamHomeId = dreamHome.getDreamHomeId();
        TimeWindow window = TimeWindow.from(today);

        long windowStartBalance = defaultIfNull(
                savingsHistoryMapper.sumBeforeDate(dreamHomeId, window.windowStartDateTime()),
                0L
        );

        List<SavingsHistory> transactions = defaultIfNull(
                savingsHistoryMapper.findByDreamHomeIdAndDateRange(dreamHomeId, window.windowStartDateTime(), window.windowEndDateTime()),
                List.of()
        );

        List<ChartData> chartData = buildChartData(window.windowStart(), window.windowEnd(), windowStartBalance, transactions);

        long totalAsset = defaultIfNull(dreamHome.getCurrentSavedAmount(), 0L);
        long growthAmount = totalAsset - windowStartBalance;
        double growthRate = windowStartBalance > 0
                ? Math.round((growthAmount * 1000.0) / windowStartBalance) / 10.0
                : 0.0;

        return new AssetsData(totalAsset, growthAmount, growthRate, chartData);
    }

    private List<ChartData> buildChartData(LocalDate windowStart, LocalDate windowEnd, long startBalance, List<SavingsHistory> transactions) {
        List<ChartData> result = new ArrayList<>();
        Map<LocalDate, Long> dailyNetByDate = aggregateDailyNet(transactions);

        long runningBalance = startBalance;
        for (LocalDate date = windowStart; !date.isAfter(windowEnd); date = date.plusDays(1)) {
            runningBalance += dailyNetByDate.getOrDefault(date, 0L);
            result.add(new ChartData(date.format(DATE_FORMATTER), runningBalance));
        }

        return result;
    }

    private LocalDate toKstDate(LocalDateTime createdAt) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.toLocalDate();
    }

    private static LocalDateTime startOfDayKst(LocalDate date) {
        return date.atStartOfDay();
    }

    private static LocalDateTime endOfDayKst(LocalDate date) {
        return date.plusDays(1).atStartOfDay().minusNanos(1);
    }

    private Map<LocalDate, Long> aggregateDailyNet(List<SavingsHistory> transactions) {
        Map<LocalDate, Long> dailyNetByDate = new HashMap<>();
        for (SavingsHistory tx : transactions) {
            if (tx.getCreatedAt() == null || tx.getSaveType() == null || tx.getAmount() == null) {
                log.warn("Skipping savingsHistory with null fields. id={}", tx.getSavingsId());
                continue;
            }
            LocalDate txDate = toKstDate(tx.getCreatedAt());
            dailyNetByDate.merge(txDate, tx.getSignedAmount(), Long::sum);
        }
        return dailyNetByDate;
    }

    private <T> T defaultIfNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String normalizeToGugun(String regionName) {
        if (regionName == null) {
            return null;
        }
        String normalized = regionName.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        String[] tokens = normalized.split("\\s+");
        return tokens[tokens.length - 1];
    }

    private record ResolvedLevel(int level, GrowthLevel growthLevel) {}

    private record TimeWindow(LocalDate windowStart, LocalDate windowEnd, LocalDateTime windowStartDateTime, LocalDateTime windowEndDateTime) {
        private static TimeWindow from(LocalDate todayKst) {
            LocalDate start = todayKst.minusDays(CHART_WINDOW_DAYS - 1);
            return new TimeWindow(start, todayKst, startOfDayKst(start), endOfDayKst(todayKst));
        }
    }
}
