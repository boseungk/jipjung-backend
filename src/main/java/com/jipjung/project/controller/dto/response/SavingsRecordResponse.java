package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.DreamHome;
import com.jipjung.project.domain.GrowthLevel;
import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.annotation.Nullable;

/**
 * 저축 기록 응답 DTO
 */
@Schema(description = "저축 기록 응답")
public record SavingsRecordResponse(

        @Schema(description = "드림홈 저축 상태")
        DreamHomeStatus dreamHomeStatus,

        @Schema(description = "성장(집 짓기) 결과")
        GrowthResult growth,

        @Schema(description = "목표 XP 진행 정보")
        GoalExpProgressResponse goalExpProgress,

        @Schema(description = "스트릭 정보 (오늘 첫 저축 시에만 포함)", nullable = true)
        @Nullable
        StreakInfo streakInfo
) {

    /**
     * 드림홈 저축 상태
     */
    @Schema(description = "드림홈 저축 상태")
    public record DreamHomeStatus(
            @Schema(description = "현재 누적 저축액 (원)", example = "51000000")
            Long currentSavedAmount,

            @Schema(description = "목표 금액 (원)", example = "300000000")
            Long targetAmount,

            @Schema(description = "달성률 (%)", example = "17.0")
            double achievementRate,

            @Schema(description = "목표 달성 여부", example = "false")
            boolean isCompleted,

            @Schema(description = "이번 저축으로 목표 달성 여부", example = "false")
            boolean justCompleted,

            @Schema(description = "완성된 컬렉션 ID (완료 시)", example = "12", nullable = true)
            @Nullable
            Long completedCollectionId,

            @Schema(description = "다음 목표 설정 가능 여부", example = "false")
            boolean nextGoalAvailable
    ) {
    }

    /**
     * 성장(집 짓기) 결과
     */
    @Schema(description = "성장 결과")
    public record GrowthResult(
            @Schema(description = "결과 유형 (SUCCESS: 성공, FAIL: 실패)", example = "SUCCESS")
            String resultType,

            @Schema(description = "경험치 변화량", example = "100")
            int expChange,

            @Schema(description = "현재 누적 경험치", example = "1350")
            int currentExp,

            @Schema(description = "다음 레벨까지 필요 경험치", example = "2000")
            int maxExp,

            @Schema(description = "현재 레벨", example = "5")
            int level,

            @Schema(description = "레벨업 여부", example = "false")
            boolean isLevelUp,

            @Schema(description = "레벨 라벨", example = "1층 골조 공사")
            String levelLabel
    ) {
    }

    // =========================================================================
    // 팩토리 메서드
    // =========================================================================

    /**
     * 기존 팩토리 메서드 (하위 호환성)
     */
    public static SavingsRecordResponse from(
            DreamHome dreamHome,
            int expChange,
            User user,
            GrowthLevel currentLevel,
            boolean isLevelUp
    ) {
        return from(
                dreamHome,
                expChange,
                user,
                currentLevel,
                isLevelUp,
                null,
                dreamHome.isCompleted(),
                false,
                null,
                null
        );
    }

    /**
     * 스트릭 정보 포함 팩토리 메서드
     *
     * @param dreamHome    드림홈 정보
     * @param expChange    경험치 변화량
     * @param user         사용자 정보
     * @param currentLevel 현재 레벨 정보
     * @param isLevelUp    레벨업 여부
     * @param streakInfo   스트릭 정보 (nullable)
     */
    public static SavingsRecordResponse from(
            DreamHome dreamHome,
            int expChange,
            User user,
            GrowthLevel currentLevel,
            boolean isLevelUp,
            StreakInfo streakInfo,
            boolean isCompleted,
            boolean justCompleted,
            Long completedCollectionId,
            GoalExpProgressResponse goalExpProgress
    ) {
        // 드림홈 상태
        DreamHomeStatus status = new DreamHomeStatus(
                dreamHome.getCurrentSavedAmount(),
                dreamHome.getTargetAmount(),
                dreamHome.getAchievementRate(),
                isCompleted,
                justCompleted,
                completedCollectionId,
                isCompleted
        );

        // 성장 결과
        GrowthResult growth = new GrowthResult(
                expChange >= 0 ? "SUCCESS" : "FAIL",
                expChange,
                user.getCurrentExp() != null ? user.getCurrentExp() : 0,
                currentLevel != null ? currentLevel.getRequiredExp() : 0,
                user.getCurrentLevel() != null ? user.getCurrentLevel() : 1,
                isLevelUp,
                currentLevel != null ? currentLevel.getStepName() : "터파기"
        );

        return new SavingsRecordResponse(status, growth, goalExpProgress, streakInfo);
    }
}
