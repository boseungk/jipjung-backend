package com.jipjung.project.controller.dto.response;

import com.jipjung.project.service.CollectionService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "목표 XP 진행 정보")
public record GoalExpProgressResponse(
        @Schema(description = "목표 XP") Integer targetExp,
        @Schema(description = "현재 XP") Integer totalExp,
        @Schema(description = "XP 진행률 (%)") Double expProgress,
        @Schema(description = "현재 여정 단계 (1-11)") Integer currentPhase
) {
    public static GoalExpProgressResponse from(CollectionService.GoalProgress progress) {
        if (progress == null || progress.targetExp() <= 0) {
            return null;
        }
        return new GoalExpProgressResponse(
                progress.targetExp(),
                progress.totalExp(),
                progress.expProgress(),
                progress.currentPhase()
        );
    }
}
