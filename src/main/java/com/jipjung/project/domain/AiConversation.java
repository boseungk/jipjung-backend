package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 대화 도메인 엔티티
 * <p>
 * 사용자의 지출 정보, AI 분석/판결 결과, 변명 정보를 저장합니다.
 * 상태: PENDING → ANALYZED → JUDGED
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {

    private Long conversationId;
    private Long userId;

    // 영수증 정보
    private Long amount;
    private String storeName;
    private String category;           // SpendingCategory enum 값
    private LocalDate paymentDate;
    private String memo;
    private String receiptImageUrl;    // 이미지 URL (선택)

    // AI 분석/판결 결과 (JSON)
    private String analysisResultJson;
    private String judgmentResultJson;

    // 변명 정보
    private String selectedExcuseId;
    private String customExcuse;

    // 판결 핵심값 (역정규화)
    private String judgmentResult;     // REASONABLE, WASTE
    private Integer judgmentScore;     // 0-100
    private Integer expChange;         // +50 또는 -30

    // 상태 관리
    private String status;             // PENDING, ANALYZED, JUDGED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * 분석이 완료되었는지 확인
     */
    public boolean isAnalyzed() {
        return ConversationStatus.ANALYZED.name().equals(status) 
            || ConversationStatus.JUDGED.name().equals(status);
    }

    /**
     * 판결이 완료되었는지 확인
     */
    public boolean isJudged() {
        return ConversationStatus.JUDGED.name().equals(status);
    }

    /**
     * 분석 결과 업데이트
     */
    public void updateAnalysis(String analysisResultJson) {
        this.analysisResultJson = analysisResultJson;
        this.status = ConversationStatus.ANALYZED.name();
    }

    /**
     * 판결 결과 업데이트
     */
    public void updateJudgment(String selectedExcuseId, String customExcuse,
                               String judgmentResult, int judgmentScore, int expChange,
                               String judgmentResultJson) {
        this.selectedExcuseId = selectedExcuseId;
        this.customExcuse = customExcuse;
        this.judgmentResult = judgmentResult;
        this.judgmentScore = judgmentScore;
        this.expChange = expChange;
        this.judgmentResultJson = judgmentResultJson;
        this.status = ConversationStatus.JUDGED.name();
    }

    /**
     * 이미지 추출 대기 중인지 확인
     */
    public boolean isExtracting() {
        return ConversationStatus.EXTRACTING.name().equals(status);
    }

    /**
     * 이미지 추출 완료 상태로 전환 (confirm 대기)
     */
    public void updateToExtracting(String analysisResultJson) {
        this.analysisResultJson = analysisResultJson;
        this.status = ConversationStatus.EXTRACTING.name();
    }

    /**
     * 지출 정보 업데이트 (confirm 시 사용)
     */
    public void updateSpendingInfo(Long amount, String storeName, String category,
                                   LocalDate paymentDate, String memo) {
        this.amount = amount;
        this.storeName = storeName;
        this.category = category;
        this.paymentDate = paymentDate;
        this.memo = memo;
    }
}
