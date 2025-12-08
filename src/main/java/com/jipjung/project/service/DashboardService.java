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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private final ThemeAssetMapper themeAssetMapper;
    private final DreamHomeMapper dreamHomeMapper;
    private final SavingsHistoryMapper savingsHistoryMapper;
    private final StreakHistoryMapper streakHistoryMapper;
    private final DsrHistoryMapper dsrHistoryMapper;
    private final UserPreferredAreaMapper userPreferredAreaMapper;
    private final ApartmentDealMapper apartmentDealMapper;
    private final DsrService dsrService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DashboardService(
            UserMapper userMapper,
            GrowthLevelMapper growthLevelMapper,
            ThemeAssetMapper themeAssetMapper,
            DreamHomeMapper dreamHomeMapper,
            SavingsHistoryMapper savingsHistoryMapper,
            StreakHistoryMapper streakHistoryMapper,
            DsrHistoryMapper dsrHistoryMapper,
            UserPreferredAreaMapper userPreferredAreaMapper,
            ApartmentDealMapper apartmentDealMapper,
            DsrService dsrService,
            ObjectMapper objectMapper
    ) {
        this(
                userMapper,
                growthLevelMapper,
                themeAssetMapper,
                dreamHomeMapper,
                savingsHistoryMapper,
                streakHistoryMapper,
                dsrHistoryMapper,
                userPreferredAreaMapper,
                apartmentDealMapper,
                dsrService,
                objectMapper,
                Clock.system(ZONE_KST)
        );
    }

    public DashboardService(
            UserMapper userMapper,
            GrowthLevelMapper growthLevelMapper,
            ThemeAssetMapper themeAssetMapper,
            DreamHomeMapper dreamHomeMapper,
            SavingsHistoryMapper savingsHistoryMapper,
            StreakHistoryMapper streakHistoryMapper,
            DsrHistoryMapper dsrHistoryMapper,
            UserPreferredAreaMapper userPreferredAreaMapper,
            ApartmentDealMapper apartmentDealMapper,
            DsrService dsrService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.growthLevelMapper = growthLevelMapper;
        this.themeAssetMapper = themeAssetMapper;
        this.dreamHomeMapper = dreamHomeMapper;
        this.savingsHistoryMapper = savingsHistoryMapper;
        this.streakHistoryMapper = streakHistoryMapper;
        this.dsrHistoryMapper = dsrHistoryMapper;
        this.userPreferredAreaMapper = userPreferredAreaMapper;
        this.apartmentDealMapper = apartmentDealMapper;
        this.dsrService = dsrService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 대시보드 통합 데이터 조회
     *
     * @param userId 사용자 ID
     * @return 대시보드 응답 DTO
     * @throws ResourceNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public DashboardResponse getDashboard(Long userId) {
        // 1. User 조회 (is_deleted=false, 없으면 예외)
        User user = findUserOrThrow(userId);

        // 2. GrowthLevel 조회 (없으면 기본 레벨로 재조회)
        ResolvedLevel resolvedLevel = resolveGrowthLevel(resolveUserLevel(user));
        int userLevel = resolvedLevel.level();
        GrowthLevel level = resolvedLevel.growthLevel();

        // 3. totalSteps 조회
        int totalSteps = resolveTotalSteps();

        // 4. DreamHome 조회 (없으면 null)
        DreamHome dreamHome = dreamHomeMapper.findActiveByUserId(userId);

        // 5. ThemeAsset 조회 (fallback + 로깅)
        ThemeAsset themeAsset = resolveThemeAsset(user.getSelectedThemeId(), userLevel);

        // 6. Streak 조회
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<StreakHistory> weeklyStreaks = streakHistoryMapper.findByUserIdAndWeek(userId, weekStart, weekEnd);
        boolean todayParticipated = streakHistoryMapper.existsByUserIdAndDate(userId, today);

        // 7. Assets 데이터 구축 (윈도우 기반)
        AssetsData assetsData = buildAssetsData(dreamHome, today);

        // 8. DSR 계산 - PRO 결과 우선 (Phase 2)
        DsrCalculationContext dsrContext = resolveDsrContext(userId, user);
        DsrSection dsrSection = DsrSection.from(user, dsrContext.dsrResult(), dsrContext.recognizedAnnualIncome());

        // 9. Gap Analysis 계산 (Phase 2)
        GapAnalysisSection gapAnalysis = buildGapAnalysis(userId, user, dreamHome, dsrContext.maxLoanAmount());

        // 10. 응답 생성
        return DashboardResponse.from(
                user, level, dreamHome, weeklyStreaks,
                todayParticipated, assetsData, themeAsset, totalSteps, dsrSection, gapAnalysis
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
    private GapAnalysisSection buildGapAnalysis(Long userId, User user, DreamHome dreamHome, long maxLoanAmount) {
        if (dreamHome != null) {
            // 목표 설정됨
            return GapAnalysisSection.from(dreamHome, user, maxLoanAmount);
        } else {
            // 목표 미설정 → 선호 지역 평균 시세로 임시 목표
            long regionAvgPrice = getRegionAveragePrice(userId);
            return GapAnalysisSection.forNoTarget(user, maxLoanAmount, regionAvgPrice);
        }
    }

    /**
     * 선호 지역 평균 시세 조회
     * - UserPreferredArea 테이블에서 첫 번째 지역
     * - 해당 지역의 최근 거래 평균가
     * - 실패 시 기본값(9.5억) 반환
     */
    private long getRegionAveragePrice(Long userId) {
        try {
            List<String> preferredAreas = userPreferredAreaMapper.findByUserId(userId);
            if (preferredAreas == null || preferredAreas.isEmpty()) {
                return DEFAULT_REGION_AVG_PRICE;
            }

            String firstArea = preferredAreas.get(0);
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

    private int resolveUserLevel(User user) {
        return user.getCurrentLevel() != null ? user.getCurrentLevel() : DEFAULT_LEVEL;
    }

    private int resolveTotalSteps() {
        int count = growthLevelMapper.countAll();
        return count > 0 ? count : DEFAULT_TOTAL_STEPS;
    }

    private ThemeAsset resolveThemeAsset(Integer selectedThemeId, int level) {
        if (selectedThemeId != null) {
            ThemeAsset asset = themeAssetMapper.findByThemeAndLevel(selectedThemeId, level);
            if (asset != null) {
                return asset;
            }
            log.warn("Theme {} not found for level {}. Falling back to default theme.", selectedThemeId, level);
        }

        ThemeAsset fallback = themeAssetMapper.findByThemeAndLevel(DEFAULT_THEME_ID, level);
        if (fallback != null) {
            return fallback;
        }

        log.error("Default theme asset not found for level {}. Using default image.", level);
        return ThemeAsset.defaultAsset();
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
                savingsHistoryMapper.sumBeforeDate(dreamHomeId, window.windowStartUtc()),
                0L
        );

        List<SavingsHistory> transactions = defaultIfNull(
                savingsHistoryMapper.findByDreamHomeIdAndDateRange(dreamHomeId, window.windowStartUtc(), window.windowEndUtc()),
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

    private LocalDate toKstDate(LocalDateTime createdAtUtc) {
        return createdAtUtc
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZONE_KST)
                .toLocalDate();
    }

    private static LocalDateTime startOfDayUtc(LocalDate date) {
        ZonedDateTime kstStart = date.atStartOfDay(ZONE_KST);
        return kstStart.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LocalDateTime endOfDayUtc(LocalDate date) {
        ZonedDateTime kstEnd = date.plusDays(1).atStartOfDay(ZONE_KST).minusNanos(1);
        return kstEnd.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
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

    private record ResolvedLevel(int level, GrowthLevel growthLevel) {}

    private record TimeWindow(LocalDate windowStart, LocalDate windowEnd, LocalDateTime windowStartUtc, LocalDateTime windowEndUtc) {
        private static TimeWindow from(LocalDate todayKst) {
            LocalDate start = todayKst.minusDays(CHART_WINDOW_DAYS - 1);
            return new TimeWindow(start, todayKst, startOfDayUtc(start), endOfDayUtc(todayKst));
        }
    }
}
