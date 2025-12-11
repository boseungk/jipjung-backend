package com.jipjung.project.controller.dto.response;

import com.jipjung.project.ai.dto.AiJudgmentOutput;
import com.jipjung.project.domain.GrowthLevel;
import com.jipjung.project.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 판결 응답 DTO
 */
@Schema(description = "판결 응답")
public record JudgmentResponse(

    @Schema(description = "판결 결과")
    Judgment judgment,

    @Schema(description = "성장 피드백")
    Growth growth,

    @Schema(description = "캐릭터 반응")
    Character character

) {
    /**
     * 판결 결과
     */
    public record Judgment(
        String result,      // REASONABLE, WASTE
        int score,          // 0-100
        String comment
    ) {}

    /**
     * 성장 피드백
     */
    public record Growth(
        String resultType,  // SUCCESS, FAIL
        int expChange,
        int currentExp,
        int maxExp,
        int level,
        String levelLabel,
        boolean isLevelUp,
        String warning      // 실패 시에만
    ) {}

    /**
     * 캐릭터 반응
     */
    public record Character(
        String mood,
        String script,
        String animation    // NOD, SHOUT
    ) {}

    /**
     * AI 판결 결과로부터 응답 생성
     */
    public static JudgmentResponse from(
            AiJudgmentOutput aiOutput,
            User user,
            GrowthLevel levelInfo,
            int expChange,
            boolean isLevelUp
    ) {
        boolean isWaste = "WASTE".equals(aiOutput.result());
        return new JudgmentResponse(
            new Judgment(aiOutput.result(), aiOutput.score(), aiOutput.comment()),
            new Growth(
                isWaste ? "FAIL" : "SUCCESS",
                expChange,
                user.getCurrentExp() != null ? user.getCurrentExp() : 0,
                levelInfo != null ? levelInfo.getRequiredExp() : 2000,
                user.getCurrentLevel() != null ? user.getCurrentLevel() : 1,
                levelInfo != null ? levelInfo.getStepName() : "터파기",
                isLevelUp,
                isWaste ? "공사가 지연되고 있습니다!" : null
            ),
            new Character(aiOutput.mood(), aiOutput.script(), aiOutput.animation())
        );
    }
}
