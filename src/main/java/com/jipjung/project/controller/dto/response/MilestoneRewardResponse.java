package com.jipjung.project.controller.dto.response;

import com.jipjung.project.service.StreakService;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마일스톤 보상 수령 응답 DTO
 */
@Schema(description = "마일스톤 보상 수령 응답")
public record MilestoneRewardResponse(

        @Schema(description = "마일스톤 일수", example = "7")
        int milestoneDays,

        @Schema(description = "획득 경험치", example = "100")
        int expReward,

        @Schema(description = "레벨업 여부", example = "false")
        boolean isLevelUp,

        @Schema(description = "수령 시점 연속일수", example = "8")
        int streakAtClaim,

        @Schema(description = "목표 XP 진행 정보")
        GoalExpProgressResponse goalExpProgress,

        @Schema(description = "축하 메시지", example = "🔥 7일 연속 저축 달성! 축하합니다!")
        String message
) {

    /**
     * 서비스 결과로부터 응답 생성
     */
    public static MilestoneRewardResponse from(StreakService.MilestoneRewardResult result) {
        String message = generateCelebrationMessage(result.milestoneDays());
        return new MilestoneRewardResponse(
                result.milestoneDays(),
                result.expReward(),
                result.isLevelUp(),
                result.streakAtClaim(),
                null,
                message
        );
    }

    public static MilestoneRewardResponse from(StreakService.MilestoneRewardResult result, GoalExpProgressResponse goalExpProgress) {
        String message = generateCelebrationMessage(result.milestoneDays());
        return new MilestoneRewardResponse(
                result.milestoneDays(),
                result.expReward(),
                result.isLevelUp(),
                result.streakAtClaim(),
                goalExpProgress,
                message
        );
    }

    /**
     * 마일스톤별 축하 메시지 생성
     * <p>
     * 활동 기반 스트릭 마일스톤: 7/14/21/28일
     */
    private static String generateCelebrationMessage(int milestoneDays) {
        return switch (milestoneDays) {
            case 7 -> "🔥 7일 연속 활동 달성! 1주 완료!";
            case 14 -> "🌟 14일 연속 활동! 2주 완료! 대단해요!";
            case 21 -> "💪 21일 연속 활동! 습관 형성 완료!";
            case 28 -> "🏆 28일 연속 활동! 한 달 완료! 진정한 집나무 숲지기!";
            default -> "🎉 마일스톤 보상 획득!";
        };
    }
}
