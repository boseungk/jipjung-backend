package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 대시보드 통합 응답 DTO
 * - 프로필, 목표, 스트릭, DSR, 자산, 쇼룸 섹션 포함
 */
@Schema(description = "대시보드 통합 응답")
public record DashboardResponse(
        @Schema(description = "프로필 섹션") ProfileSection profile,
        @Schema(description = "목표 섹션") GoalSection goal,
        @Schema(description = "스트릭 섹션") StreakSection streak,
        @Schema(description = "DSR 섹션") DsrSection dsr,
        @Schema(description = "자산 섹션") AssetsSection assets,
        @Schema(description = "쇼룸 섹션") ShowroomSection showroom,
        @Schema(description = "Gap 분석 섹션") GapAnalysisSection gapAnalysis
) {

    // ==========================================================================
    // Factory Method
    // ==========================================================================

    public static DashboardResponse from(
            User user,
            GrowthLevel level,
            DreamHome dreamHome,
            List<StreakHistory> weeklyStreaks,
            boolean todayParticipated,
            AssetsData assetsData,
            HouseTheme houseTheme,
            int totalSteps,
            DsrSection dsrSection,
            GapAnalysisSection gapAnalysis,
            List<String> preferredAreas,
            Long latestDealPrice,
            GoalExpProgress goalExpProgress
    ) {
        return new DashboardResponse(
                ProfileSection.from(user, level, preferredAreas),
                GoalSection.from(dreamHome, latestDealPrice, goalExpProgress),
                StreakSection.from(user, weeklyStreaks, todayParticipated),
                dsrSection,
                AssetsSection.from(assetsData),
                ShowroomSection.from(user, level, houseTheme, totalSteps),
                gapAnalysis
        );
    }

    // ==========================================================================
    // Nested Records: Profile Section
    // ==========================================================================

    @Schema(description = "프로필 섹션")
    public record ProfileSection(
            @Schema(description = "사용자 ID") Long userId,
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "이메일") String email,
            @Schema(description = "생년") Integer birthYear,
            @Schema(description = "가입일") String createdAt,
            @Schema(description = "연소득 (만원 단위)") Long annualIncome,
            @Schema(description = "기존 대출 월 상환금 (만원 단위)") Long existingLoanMonthly,
            @Schema(description = "칭호 (예: 터파기 건축가)") String title,
            @Schema(description = "상태 메시지") String statusMessage,
            @Schema(description = "현재 레벨") int level,
            @Schema(description = "레벨 진행 상황") LevelProgress levelProgress,
            @Schema(description = "선호 지역 목록 (구/군명)") List<String> preferredAreas
    ) {
        private static final String DEFAULT_TITLE = "신입 건축가";
        private static final String DEFAULT_STATUS_MESSAGE = "목표를 향해 천천히, 꾸준히 가고 있어요";
        private static final int DEFAULT_LEVEL = 1;
        private static final int DEFAULT_REQUIRED_EXP = 100;
        private static final long WON_TO_MANWON = 10000L;

        public static ProfileSection from(User user, GrowthLevel growthLevel, List<String> preferredAreas) {
            String title = growthLevel != null ? growthLevel.getTitle() : DEFAULT_TITLE;
            int currentLevel = user.getCurrentLevel() != null ? user.getCurrentLevel() : DEFAULT_LEVEL;
            int currentExp = user.getCurrentExp() != null ? user.getCurrentExp() : 0;
            int requiredExp = growthLevel != null && growthLevel.getRequiredExp() != null
                    ? growthLevel.getRequiredExp() : DEFAULT_REQUIRED_EXP;

            // 원 -> 만원 변환
            Long annualIncomeManwon = user.getAnnualIncome() != null 
                    ? user.getAnnualIncome() / WON_TO_MANWON : null;
            Long existingLoanManwon = user.getExistingLoanMonthly() != null 
                    ? user.getExistingLoanMonthly() / WON_TO_MANWON : null;

            // createdAt을 ISO 문자열로 변환
            String createdAtStr = user.getCreatedAt() != null 
                    ? user.getCreatedAt().toString() : null;

            return new ProfileSection(
                    user.getId(),
                    user.getNickname(),
                    user.getEmail(),
                    user.getBirthYear(),
                    createdAtStr,
                    annualIncomeManwon,
                    existingLoanManwon,
                    title,
                    DEFAULT_STATUS_MESSAGE,
                    currentLevel,
                    new LevelProgress(currentExp, requiredExp),
                    preferredAreas != null ? List.copyOf(preferredAreas) : List.of()
            );
        }
    }

    @Schema(description = "레벨 진행 상황")
    public record LevelProgress(
            @Schema(description = "현재 경험치") int currentExp,
            @Schema(description = "필요 경험치") int targetExp,
            @Schema(description = "진행률 (%)") double percent,
            @Schema(description = "남은 경험치") int remainingExp
    ) {
        public LevelProgress(int currentExp, int targetExp) {
            this(
                    currentExp,
                    targetExp,
                    targetExp > 0 ? Math.round((currentExp * 1000.0) / targetExp) / 10.0 : 0.0,
                    Math.max(0, targetExp - currentExp)
            );
        }
    }

    // ==========================================================================
    // Nested Records: Goal Section
    // ==========================================================================

    /**
     * 목표 섹션
     * <p>
     * V2 리팩토링: 저축 목표(targetAmount)와 매물 정보(linkedProperty)를 분리
     * <ul>
     *   <li>targetAmount: 사용자가 설정한 저축 목표 (게이미피케이션 기준)</li>
     *   <li>linkedProperty: 참조용 매물 정보 (Gap 표시용, nullable)</li>
     * </ul>
     */
    @Schema(description = "목표 섹션")
    public record GoalSection(
            @Schema(description = "드림홈 ID") Long dreamHomeId,
            @Schema(description = "목표 아파트명 (houseName 우선, 없으면 아파트명)") String targetPropertyName,
            @Schema(description = "사용자 정의 집 이름 (nullable)") String houseName,
            @Schema(description = "저축 목표 (사용자 설정)") long targetAmount,
            @Schema(description = "저축 금액") long savedAmount,
            @Schema(description = "남은 금액 (목표 - 저축)") long remainingAmount,
            @Schema(description = "달성률 (%)") double achievementRate,
            @Schema(description = "완료 여부") boolean isCompleted,
            @Schema(description = "목표 XP") Integer targetExp,
            @Schema(description = "현재 XP") Integer totalExp,
            @Schema(description = "XP 진행률 (%)") Double expProgress,
            @Schema(description = "현재 여정 단계 (1-11)") Integer currentPhase,
            @Schema(description = "연결된 매물 정보 (참조용, nullable)") LinkedProperty linkedProperty
    ) {
        private static final String NO_GOAL_MESSAGE = "목표를 설정해주세요";

        public static GoalSection from(DreamHome dreamHome, Long latestDealPrice, GoalExpProgress goalExpProgress) {
            GoalExpProgress safeProgress = goalExpProgress != null ? goalExpProgress : GoalExpProgress.empty();
            if (dreamHome == null) {
                return new GoalSection(
                        null,
                        NO_GOAL_MESSAGE,
                        null,
                        0,
                        0,
                        0,
                        0.0,
                        false,
                        safeProgress.targetExp(),
                        safeProgress.totalExp(),
                        safeProgress.expProgress(),
                        safeProgress.currentPhase(),
                        null
                );
            }

            String aptName = dreamHome.getApartment() != null
                    ? dreamHome.getApartment().getAptNm()
                    : NO_GOAL_MESSAGE;
            
            // houseName이 있으면 그것을 사용, 없으면 aptName
            String houseName = dreamHome.getHouseName();
            String displayName = (houseName != null && !houseName.isBlank()) ? houseName : aptName;

            long targetAmount = dreamHome.getTargetAmount() != null ? dreamHome.getTargetAmount() : 0;
            long savedAmount = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0;

            // V2: 연결된 매물 정보 추출 (최신 거래가는 외부에서 조회)
            LinkedProperty linkedProperty = buildLinkedProperty(dreamHome, latestDealPrice);

            return new GoalSection(
                    dreamHome.getDreamHomeId(),
                    displayName,
                    houseName,
                    targetAmount,
                    savedAmount,
                    dreamHome.getRemainingAmount(),
                    dreamHome.getAchievementRate(),
                    dreamHome.isCompleted(),
                    safeProgress.targetExp(),
                    safeProgress.totalExp(),
                    safeProgress.expProgress(),
                    safeProgress.currentPhase(),
                    linkedProperty
            );
        }

        /**
         * 하위 호환성을 위한 오버로드 메서드
         */
        public static GoalSection from(DreamHome dreamHome) {
            return from(dreamHome, null, null);
        }

        /**
         * 연결된 매물 정보 추출
         * <p>
         * V2: 매물이 연결되어 있으면 Gap 정보 포함, 없으면 null
         * @param dreamHome 드림홈 엔티티
         * @param latestDealPrice 최신 거래가 (원 단위, nullable - DashboardService에서 조회)
         */
        private static LinkedProperty buildLinkedProperty(DreamHome dreamHome, Long latestDealPrice) {
            Apartment apt = dreamHome.getApartment();
            if (apt == null) {
                return null;
            }

            // 최신 거래가 (외부에서 전달받음)
            Long price = latestDealPrice;

            long savedAmount = dreamHome.getCurrentSavedAmount() != null
                    ? dreamHome.getCurrentSavedAmount() : 0L;

            // Gap = 시세 - 저축액 (항상 0 이상)
            long gap = price != null ? Math.max(0, price - savedAmount) : 0L;

            return new LinkedProperty(apt.getAptSeq(), apt.getAptNm(), price, gap);
        }
    }

    @Schema(description = "목표 XP 진행 정보")
    public record GoalExpProgress(
            @Schema(description = "목표 XP") Integer targetExp,
            @Schema(description = "현재 XP") Integer totalExp,
            @Schema(description = "XP 진행률 (%)") Double expProgress,
            @Schema(description = "현재 여정 단계 (1-11)") Integer currentPhase
    ) {
        public static GoalExpProgress empty() {
            return new GoalExpProgress(null, null, null, null);
        }
    }

    /**
     * 연결된 매물 정보 (V2)
     * <p>
     * 저축 목표와 분리된 참조용 매물 정보.
     * Gap 정보를 통해 실제 집값과의 차이를 표시.
     */
    @Schema(description = "연결된 매물 정보 (참조용)")
    public record LinkedProperty(
            @Schema(description = "아파트 코드") String aptSeq,
            @Schema(description = "아파트명") String name,
            @Schema(description = "실제 시세 (원, nullable)") Long price,
            @Schema(description = "Gap (시세 - 현재 저축액, 항상 0 이상)") long gap
    ) {}


    // ==========================================================================
    // Nested Records: Streak Section
    // ==========================================================================

    @Schema(description = "스트릭 섹션")
    public record StreakSection(
            @Schema(description = "현재 스트릭") int currentStreak,
            @Schema(description = "최대 스트릭") int maxStreak,
            @Schema(description = "오늘 참여 여부") boolean isTodayParticipated,
            @Schema(description = "보상 가능 여부") boolean rewardAvailable,
            @Schema(description = "주간 상태 (월~일)") List<DayStatus> weeklyStatus
    ) {
        private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

        public static StreakSection from(User user, List<StreakHistory> weeklyStreaks, boolean todayParticipated) {
            int currentStreak = user.getStreakCount() != null ? user.getStreakCount() : 0;
            int maxStreak = user.getMaxStreak() != null ? user.getMaxStreak() : 0;

            // 참여한 날짜 Set 생성
            Set<LocalDate> participatedDates = weeklyStreaks.stream()
                    .map(StreakHistory::getStreakDate)
                    .collect(Collectors.toSet());

            // 이번 주 월~일 status 생성
            List<DayStatus> weeklyStatus = buildWeeklyStatus(participatedDates);

            return new StreakSection(
                    currentStreak,
                    maxStreak,
                    todayParticipated,
                    !todayParticipated,  // 오늘 미참여 시 보상 가능
                    weeklyStatus
            );
        }

        private static List<DayStatus> buildWeeklyStatus(Set<LocalDate> participatedDates) {
            LocalDate today = LocalDate.now(ZONE_KST);
            LocalDate monday = today.with(DayOfWeek.MONDAY);

            return java.util.stream.IntStream.range(0, 7)
                    .mapToObj(i -> {
                        LocalDate date = monday.plusDays(i);
                        DayOfWeek day = date.getDayOfWeek();
                        boolean achieved = participatedDates.contains(date);
                        return new DayStatus(getDayLabel(day), achieved);
                    })
                    .toList();
        }

        private static String getDayLabel(DayOfWeek day) {
            return switch (day) {
                case MONDAY -> "월";
                case TUESDAY -> "화";
                case WEDNESDAY -> "수";
                case THURSDAY -> "목";
                case FRIDAY -> "금";
                case SATURDAY -> "토";
                case SUNDAY -> "일";
            };
        }
    }

    @Schema(description = "요일별 스트릭 상태")
    public record DayStatus(
            @Schema(description = "요일 (월~일)") String day,
            @Schema(description = "참여 완료 여부") boolean achieved
    ) {}

    // ==========================================================================
    // Nested Records: DSR Section
    // ==========================================================================

    @Schema(description = "DSR 섹션")
    public record DsrSection(
            @Schema(description = "DSR 비율 (%)") double dsrPercent,
            @Schema(description = "등급 라벨") String gradeLabel,
            @Schema(description = "등급 색상") String gradeColor,
            @Schema(description = "금융 정보") FinancialInfo financialInfo
    ) {
        private static final String NO_INCOME_LABEL = "소득 정보 없음";
        private static final String NO_INCOME_COLOR = "GRAY";
        private static final String DANGER_LABEL = "위험";
        private static final String DANGER_COLOR = "RED";
        private static final String SAFE_LABEL = "매우 안전";
        private static final String SAFE_COLOR = "GREEN";

        /**
         * 기존 메서드 (deprecated) - DashboardService 마이그레이션 완료 후 삭제 예정
         * 
         * @deprecated Use {@link #from(User, com.jipjung.project.dsr.DsrResult)} instead
         */
        @Deprecated
        public static DsrSection from(User user) {
            FinancialInfo financialInfo = FinancialInfo.from(user);
            long monthlyIncome = financialInfo.monthlyIncome();
            long existingLoan = financialInfo.existingLoanRepayment();

            // 소득 없음 + 대출 있음 → 최대 위험
            if (monthlyIncome <= 0 && existingLoan > 0) {
                return new DsrSection(
                        100.0,
                        DANGER_LABEL,
                        DANGER_COLOR,
                        financialInfo
                );
            }

            // 소득/대출 모두 없음 → 소득 정보 없음으로 노출
            if (monthlyIncome <= 0 && existingLoan == 0) {
                return new DsrSection(
                        0.0,
                        NO_INCOME_LABEL,
                        NO_INCOME_COLOR,
                        financialInfo
                );
            }

            // 기존 대출 없음 → 매우 안전
            if (existingLoan == 0) {
                return new DsrSection(
                        0.0,
                        SAFE_LABEL,
                        SAFE_COLOR,
                        financialInfo
                );
            }

            // DSR 계산
            double dsrPercent = monthlyIncome > 0
                    ? Math.round((existingLoan * 1000.0) / monthlyIncome) / 10.0
                    : 0.0;

            DsrGrade grade = DsrGrade.fromPercent(dsrPercent);

            return new DsrSection(
                    dsrPercent,
                    grade.label,
                    grade.color,
                    financialInfo
            );
        }

        /**
         * 신규 메서드 - DsrResult 연동
         * <p>
         * DsrCalculator에서 계산된 등급을 그대로 사용하여 통합 등급 유지.
         *
         * @param user      사용자 정보
         * @param dsrResult DSR 계산 결과
         * @param recognizedAnnualIncome 장래소득 인정 반영된 연소득 (표시/계산 일관성용)
         * @return DsrSection
         */
        public static DsrSection from(
                User user,
                com.jipjung.project.dsr.DsrResult dsrResult,
                long recognizedAnnualIncome
        ) {
            FinancialInfo financialInfo = FinancialInfo.from(user, recognizedAnnualIncome);

            // DsrResult의 등급을 화면용 라벨/색상으로 변환
            String gradeLabel = switch (dsrResult.grade()) {
                case "SAFE" -> "안전";
                case "WARNING" -> "주의";
                case "RESTRICTED" -> "위험";
                default -> dsrResult.grade();
            };
            String gradeColor = switch (dsrResult.grade()) {
                case "SAFE" -> "GREEN";
                case "WARNING" -> "YELLOW";
                case "RESTRICTED" -> "RED";
                default -> "GRAY";
            };

            return new DsrSection(
                    dsrResult.currentDsrPercent(),
                    gradeLabel,
                    gradeColor,
                    financialInfo
            );
        }

        public static DsrSection from(User user, com.jipjung.project.dsr.DsrResult dsrResult) {
            long fallbackRecognizedIncome = user.getAnnualIncome() != null ? user.getAnnualIncome() : 0L;
            return from(user, dsrResult, fallbackRecognizedIncome);
        }

        private enum DsrGrade {
            SAFE("안전", "GREEN", 0, 30),
            CAUTION("주의", "YELLOW", 30, 50),
            DANGER("위험", "RED", 50, 100);

            final String label;
            final String color;
            final int minPercent;
            final int maxPercent;

            DsrGrade(String label, String color, int minPercent, int maxPercent) {
                this.label = label;
                this.color = color;
                this.minPercent = minPercent;
                this.maxPercent = maxPercent;
            }

            static DsrGrade fromPercent(double percent) {
                for (DsrGrade grade : values()) {
                    if (percent >= grade.minPercent && percent < grade.maxPercent) {
                        return grade;
                    }
                }
                return DANGER;
            }
        }
    }

    @Schema(description = "금융 정보")
    public record FinancialInfo(
            @Schema(description = "월 소득") long monthlyIncome,
            @Schema(description = "기존 대출 상환액") long existingLoanRepayment,
            @Schema(description = "가용 상환 여력 (월 소득의 40% - 기존 대출)") long availableCapacity
    ) {
        public static FinancialInfo from(User user) {
            return from(user, 0L);
        }

        public static FinancialInfo from(User user, long recognizedAnnualIncome) {
            long monthlyIncome = user.getMonthlyIncome();
            if (recognizedAnnualIncome > 0) {
                monthlyIncome = recognizedAnnualIncome / 12;
            }
            long existingLoan = user.getExistingLoanMonthly() != null ? user.getExistingLoanMonthly() : 0;
            long availableCapacity = Math.max(0, (long) (monthlyIncome * 0.4) - existingLoan);

            return new FinancialInfo(monthlyIncome, existingLoan, availableCapacity);
        }
    }

    // ==========================================================================
    // Nested Records: Assets Section
    // ==========================================================================

    @Schema(description = "자산 섹션")
    public record AssetsSection(
            @Schema(description = "총 자산") long totalAsset,
            @Schema(description = "성장 금액 (30일 기준)") long growthAmount,
            @Schema(description = "성장률 (%)") double growthRate,
            @Schema(description = "차트 데이터") List<ChartData> chartData
    ) {
        public static AssetsSection from(AssetsData data) {
            if (data == null) {
                return new AssetsSection(0, 0, 0.0, List.of());
            }
            return new AssetsSection(
                    data.totalAsset(),
                    data.growthAmount(),
                    data.growthRate(),
                    data.chartData()
            );
        }
    }

    @Schema(description = "차트 데이터 포인트")
    public record ChartData(
            @Schema(description = "날짜 (yyyy-MM-dd)") String date,
            @Schema(description = "잔액") long balance
    ) {}

    /**
     * Service에서 생성하는 자산 데이터 컨테이너
     */
    public record AssetsData(
            long totalAsset,
            long growthAmount,
            double growthRate,
            List<ChartData> chartData
    ) {}

    // ==========================================================================
    // Nested Records: Showroom Section
    // ==========================================================================

    @Schema(description = "쇼룸 섹션 (집짓기 시각화)")
    public record ShowroomSection(
            @Schema(description = "현재 단계") int currentStep,
            @Schema(description = "총 단계 수") int totalSteps,
            @Schema(description = "단계명") String stepTitle,
            @Schema(description = "단계 설명") String stepDescription,
            @Schema(description = "선택된 테마 ID") Integer themeId,
            @Schema(description = "선택된 테마 코드") String themeCode,
            @Schema(description = "선택된 테마 이름") String themeName,
            @Schema(description = "이미지 URL") String imageUrl,
            // Phase 2: 인테리어 진행 상태
            @Schema(description = "현재 트랙 (house/furniture)") String buildTrack,
            @Schema(description = "인테리어 단계 (0-5)") int furnitureStage,
            @Schema(description = "인테리어 단계 내 EXP") int furnitureExp
    ) {
        private static final String DEFAULT_STEP_TITLE = "터파기";
        private static final String DEFAULT_STEP_DESCRIPTION = "기초 공사를 시작합니다";
        private static final int DEFAULT_TOTAL_STEPS = 7;

        public static ShowroomSection from(User user, GrowthLevel level, HouseTheme houseTheme, int totalSteps) {
            int steps = totalSteps > 0 ? totalSteps : DEFAULT_TOTAL_STEPS;
            int rawCurrentStep = user.getCurrentLevel() != null ? user.getCurrentLevel() : 1;
            int currentStep = Math.min(Math.max(rawCurrentStep, 1), steps);

            String stepTitle = level != null && level.getStepName() != null
                    ? level.getStepName() : DEFAULT_STEP_TITLE;
            String stepDescription = level != null && level.getDescription() != null
                    ? level.getDescription() : DEFAULT_STEP_DESCRIPTION;

            Integer themeId = houseTheme != null ? houseTheme.getThemeId() : null;
            String themeCode = houseTheme != null ? houseTheme.getThemeCode() : null;
            String themeName = houseTheme != null ? houseTheme.getThemeName() : null;
            // HouseTheme.getFullImageUrl()가 CDN URL 또는 폴백 경로 반환
            String imageUrl = houseTheme != null ? houseTheme.getFullImageUrl() : "/" + HouseTheme.DEFAULT_IMAGE_PATH;

            // 인테리어 진행 상태
            String buildTrack = user.getBuildTrack() != null ? user.getBuildTrack() : "house";
            int furnitureStage = user.getFurnitureStage() != null ? user.getFurnitureStage() : 0;
            int furnitureExp = user.getFurnitureExp() != null ? user.getFurnitureExp() : 0;

            return new ShowroomSection(
                    currentStep,
                    steps,
                    stepTitle,
                    stepDescription,
                    themeId,
                    themeCode,
                    themeName,
                    imageUrl,
                    buildTrack,
                    furnitureStage,
                    furnitureExp
            );
        }
    }

    // ==========================================================================
    // Nested Records: Gap Analysis Section (Phase 2)
    // ==========================================================================

    /**
     * Gap 분석 섹션
     * <p>
     * 목표 금액에서 현재 자산, 저축, 대출 한도를 차감한 필요 저축액 계산.
     * 매물이 연결되어 있으면 목표 금액은 실거래가(최신) 기준.
     */
    @Schema(description = "Gap 분석 섹션")
    public record GapAnalysisSection(
            @Schema(description = "목표 설정 여부") boolean hasTarget,
            @Schema(description = "목표 금액 (연결 매물 시 시세, 미설정 시 지역 평균)") long targetAmount,
            @Schema(description = "현재 자산 (온보딩)") long currentAssets,
            @Schema(description = "현재 저축") long currentSavedAmount,
            @Schema(description = "추정 대출 한도") long virtualLoanLimit,
            @Schema(description = "필요 저축액") long requiredSavings,
            @Schema(description = "DSR 모드") String dsrMode
    ) {
        /**
         * 목표 설정 시 Gap 분석
         */
        public static GapAnalysisSection from(DreamHome dreamHome, User user, long maxLoanAmount, long targetAmount) {
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
         * 목표 미설정 시 Gap 분석 (임시 목표: 선호 지역 평균 시세)
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
}

