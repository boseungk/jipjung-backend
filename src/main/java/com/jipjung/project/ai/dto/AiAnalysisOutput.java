package com.jipjung.project.ai.dto;

import java.util.List;

/**
 * Gemini 분석 응답 (JSON으로 파싱)
 * <p>
 * AI가 지출을 분석하고 심문하는 내용을 담습니다.
 * IMAGE 모드에서는 영수증 이미지에서 추출한 정보도 포함합니다.
 */
public record AiAnalysisOutput(
    // 공통 필드 (MANUAL/IMAGE 모두)
    String mood,           // STRICT, NORMAL, CURIOUS, CONFUSED, ANNOYED
    String moodLabel,      // "매우 엄격함", "보통", "호기심", "혼란", "짜증"
    String script,         // 레제의 대사

    // MANUAL 모드 전용 (IMAGE confirm 후에도 사용)
    List<Excuse> suggestedExcuses,

    // IMAGE 모드 전용: 추출된 영수증 정보
    Long extractedAmount,
    String extractedStoreName,
    String extractedCategory,     // FOOD, TRANSPORT 등
    String extractedPaymentDate   // "2025-12-10" 형식
) {
    /**
     * 변명 선택지
     */
    public record Excuse(
        String id,         // STRESS, NEED, ADMIT 등
        String text,       // "스트레스 비용"
        String type        // DEFENSE, GIVE_UP
    ) {}

    /**
     * suggestedExcuses가 null인 경우 빈 리스트 반환 (NPE 방지)
     */
    public List<Excuse> suggestedExcuses() {
        return suggestedExcuses != null ? suggestedExcuses : List.of();
    }
}
