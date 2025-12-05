package com.jipjung.project.service;

import com.jipjung.project.controller.dto.response.DashboardResponse;
import com.jipjung.project.controller.dto.response.DashboardResponse.AssetsData;
import com.jipjung.project.controller.dto.response.DashboardResponse.ChartData;
import com.jipjung.project.domain.*;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_THEME_ID = 1;
    private static final int DEFAULT_LEVEL = 1;
    private static final int CHART_WINDOW_DAYS = 30; // 포함 기준 일수 (오늘 포함 30일)
    private static final int DEFAULT_TOTAL_STEPS = 7;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final AssetsData EMPTY_ASSETS = new AssetsData(0, 0, 0.0, List.of());

    private final UserMapper userMapper;
    private final GrowthLevelMapper growthLevelMapper;
    private final ThemeAssetMapper themeAssetMapper;
    private final DreamHomeMapper dreamHomeMapper;
    private final SavingsHistoryMapper savingsHistoryMapper;
    private final StreakHistoryMapper streakHistoryMapper;

    /**
     * 대시보드 통합 데이터 조회
     *
     * @param userId 사용자 ID
     * @return 대시보드 응답 DTO
     * @throws ResourceNotFoundException 사용자를 찾을 수 없는 경우
     */
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
        LocalDate today = LocalDate.now(ZONE_KST);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<StreakHistory> weeklyStreaks = streakHistoryMapper.findByUserIdAndWeek(userId, weekStart, weekEnd);
        boolean todayParticipated = streakHistoryMapper.existsByUserIdAndDate(userId, today);

        // 7. Assets 데이터 구축 (윈도우 기반)
        AssetsData assetsData = buildAssetsData(dreamHome, today);

        // 8. 응답 생성
        return DashboardResponse.from(
                user, level, dreamHome, weeklyStreaks,
                todayParticipated, assetsData, themeAsset, totalSteps
        );
    }

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

    /**
     * 테마 에셋 조회 (3단계 Fallback)
     * 1. user.selectedThemeId로 조회 → 실패 시 log.warn
     * 2. DEFAULT_THEME_ID(1)로 재조회 → 실패 시 log.error
     * 3. 기본 이미지 반환
     */
    private ThemeAsset resolveThemeAsset(Integer selectedThemeId, int level) {
        // 1차: 사용자 선택 테마
        if (selectedThemeId != null) {
            ThemeAsset asset = themeAssetMapper.findByThemeAndLevel(selectedThemeId, level);
            if (asset != null) {
                return asset;
            }
            log.warn("Theme {} not found for level {}. Falling back to default theme.", selectedThemeId, level);
        }

        // 2차: 기본 테마
        ThemeAsset fallback = themeAssetMapper.findByThemeAndLevel(DEFAULT_THEME_ID, level);
        if (fallback != null) {
            return fallback;
        }

        // 3차: 기본 이미지
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
        }
        return new ResolvedLevel(DEFAULT_LEVEL, fallback);
    }

    /**
     * 자산 데이터 구축 (윈도우 기반 차트)
     */
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

        List<SavingsHistory> transactions = savingsHistoryMapper
                .findByDreamHomeIdAndDateRange(dreamHomeId, window.windowStartUtc(), window.windowEndUtc());

        List<ChartData> chartData = buildChartData(window.windowStart(), window.windowEnd(), windowStartBalance, transactions);

        // 현재 총 자산
        long totalAsset = defaultIfNull(dreamHome.getCurrentSavedAmount(), 0L);

        // 성장 금액/률 계산
        long growthAmount = totalAsset - windowStartBalance;
        double growthRate = windowStartBalance > 0
                ? Math.round((growthAmount * 1000.0) / windowStartBalance) / 10.0
                : 0.0;

        return new AssetsData(totalAsset, growthAmount, growthRate, chartData);
    }

    /**
     * 차트 데이터 포인트 생성
     * - 첫 포인트: windowStartBalance
     * - 이후: DEPOSIT(+) / WITHDRAW(-) 누적
     */
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

    // created_at을 UTC로 간주하고 KST 날짜로 변환
    private LocalDate toKstDate(LocalDateTime createdAtUtc) {
        return createdAtUtc
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZONE_KST)
                .toLocalDate();
    }

    // KST 자정 기준을 UTC LocalDateTime으로 환산
    private static LocalDateTime startOfDayUtc(LocalDate date) {
        ZonedDateTime kstStart = date.atStartOfDay(ZONE_KST);
        return kstStart.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // KST 말일 23:59:59.999999999 기준을 UTC LocalDateTime으로 환산
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
