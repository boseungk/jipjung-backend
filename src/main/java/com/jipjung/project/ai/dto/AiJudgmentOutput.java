package com.jipjung.project.ai.dto;

/**
 * Gemini 판결 응답 (JSON으로 파싱)
 * <p>
 * AI가 변명을 듣고 최종 판결을 내리는 내용을 담습니다.
 */
public record AiJudgmentOutput(
    String result,         // REASONABLE, WASTE
    int score,             // 0-100
    String comment,        // 판결 코멘트
    String mood,           // NORMAL, ANGRY
    String script,         // 레제의 대사
    String animation       // NOD, SHOUT
) {}
