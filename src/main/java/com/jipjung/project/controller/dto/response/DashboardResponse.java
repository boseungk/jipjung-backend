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
        @Schema(description = "쇼룸 섹션") ShowroomSection showroom
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
            ThemeAsset themeAsset,
            int totalSteps
    ) {
        return new DashboardResponse(
                ProfileSection.from(user, level),
                GoalSection.from(dreamHome),
                StreakSection.from(user, weeklyStreaks, todayParticipated),
                DsrSection.from(user),
                AssetsSection.from(assetsData),
                ShowroomSection.from(user, level, themeAsset, totalSteps)
        );
    }

    // ==========================================================================
    // Nested Records: Profile Section
    // ==========================================================================

    @Schema(description = "프로필 섹션")
    public record ProfileSection(
            @Schema(description = "닉네임") String nickname,
            @Schema(description = "칭호 (예: 터파기 건축가)") String title,
            @Schema(description = "상태 메시지") String statusMessage,
            @Schema(description = "현재 레벨") int level,
            @Schema(description = "레벨 진행 상황") LevelProgress levelProgress
    ) {
        private static final String DEFAULT_TITLE = "신입 건축가";
        private static final String DEFAULT_STATUS_MESSAGE = "목표를 향해 천천히, 꾸준히 가고 있어요";
        private static final int DEFAULT_LEVEL = 1;
        private static final int DEFAULT_REQUIRED_EXP = 100;

        public static ProfileSection from(User user, GrowthLevel growthLevel) {
            String title = growthLevel != null ? growthLevel.getTitle() : DEFAULT_TITLE;
            int currentLevel = user.getCurrentLevel() != null ? user.getCurrentLevel() : DEFAULT_LEVEL;
            int currentExp = user.getCurrentExp() != null ? user.getCurrentExp() : 0;
            int requiredExp = growthLevel != null && growthLevel.getRequiredExp() != null
                    ? growthLevel.getRequiredExp() : DEFAULT_REQUIRED_EXP;

            return new ProfileSection(
                    user.getNickname(),
                    title,
                    DEFAULT_STATUS_MESSAGE,
                    currentLevel,
                    new LevelProgress(currentExp, requiredExp)
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

    @Schema(description = "목표 섹션")
    public record GoalSection(
            @Schema(description = "목표 아파트명") String targetPropertyName,
            @Schema(description = "목표 금액") long totalAmount,
            @Schema(description = "저축 금액") long savedAmount,
            @Schema(description = "남은 금액") long remainingAmount,
            @Schema(description = "달성률 (%)") double achievementRate,
            @Schema(description = "완료 여부") boolean isCompleted
    ) {
        private static final String NO_GOAL_MESSAGE = "목표를 설정해주세요";

        public static GoalSection from(DreamHome dreamHome) {
            if (dreamHome == null) {
                return new GoalSection(NO_GOAL_MESSAGE, 0, 0, 0, 0.0, false);
            }

            String aptName = dreamHome.getApartment() != null
                    ? dreamHome.getApartment().getAptNm()
                    : NO_GOAL_MESSAGE;

            long targetAmount = dreamHome.getTargetAmount() != null ? dreamHome.getTargetAmount() : 0;
            long savedAmount = dreamHome.getCurrentSavedAmount() != null ? dreamHome.getCurrentSavedAmount() : 0;

            return new GoalSection(
                    aptName,
                    targetAmount,
                    savedAmount,
                    dreamHome.getRemainingAmount(),
                    dreamHome.getAchievementRate(),
                    dreamHome.isCompleted()
            );
        }
    }

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

        public static DsrSection from(User user) {
            // 소득 정보 없음
            if (!user.hasIncomeInfo()) {
                return new DsrSection(
                        0.0,
                        NO_INCOME_LABEL,
                        NO_INCOME_COLOR,
                        FinancialInfo.from(user)
                );
            }

            long monthlyIncome = user.getMonthlyIncome();
            long existingLoan = user.getExistingLoanMonthly() != null ? user.getExistingLoanMonthly() : 0;

            // 기존 대출 없음 → 매우 안전
            if (existingLoan == 0) {
                return new DsrSection(
                        0.0,
                        "매우 안전",
                        "GREEN",
                        FinancialInfo.from(user)
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
                    FinancialInfo.from(user)
            );
        }

        private enum DsrGrade {
            VERY_SAFE("매우 안전", "GREEN", 0, 20),
            SAFE("안전", "BLUE", 20, 30),
            MODERATE("보통", "YELLOW", 30, 40),
            CAUTION("주의", "ORANGE", 40, 50),
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
            long monthlyIncome = user.getMonthlyIncome();
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
            @Schema(description = "이미지 URL") String imageUrl
    ) {
        private static final String DEFAULT_STEP_TITLE = "터파기";
        private static final String DEFAULT_STEP_DESCRIPTION = "기초 공사를 시작합니다";
        private static final int DEFAULT_TOTAL_STEPS = 7;

        public static ShowroomSection from(User user, GrowthLevel level, ThemeAsset themeAsset, int totalSteps) {
            int steps = totalSteps > 0 ? totalSteps : DEFAULT_TOTAL_STEPS;
            int rawCurrentStep = user.getCurrentLevel() != null ? user.getCurrentLevel() : 1;
            int currentStep = Math.min(Math.max(rawCurrentStep, 1), steps);

            String stepTitle = level != null && level.getStepName() != null
                    ? level.getStepName() : DEFAULT_STEP_TITLE;
            String stepDescription = level != null && level.getDescription() != null
                    ? level.getDescription() : DEFAULT_STEP_DESCRIPTION;
            String imageUrl = themeAsset != null && themeAsset.getImageUrl() != null
                    ? themeAsset.getImageUrl() : ThemeAsset.DEFAULT_IMAGE_URL;

            return new ShowroomSection(currentStep, steps, stepTitle, stepDescription, imageUrl);
        }
    }
}
