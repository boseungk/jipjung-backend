package com.jipjung.project.domain;

/**
 * AI 대화 상태 enum
 */
public enum ConversationStatus {
    PENDING,     // 분석 대기 중
    EXTRACTING,  // 이미지 추출 완료, confirm 대기
    ANALYZED,    // 분석 완료, 판결 대기
    JUDGED       // 판결 완료
}
