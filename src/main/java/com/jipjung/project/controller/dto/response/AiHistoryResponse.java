package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.AiConversation;
import com.jipjung.project.domain.SpendingCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI 분석 내역 응답 DTO
 */
@Schema(description = "AI 분석 내역")
public record AiHistoryResponse(

    @Schema(description = "대화 ID")
    Long conversationId,

    @Schema(description = "영수증 정보")
    ReceiptInfo receiptInfo,

    @Schema(description = "판결 결과 (REASONABLE/WASTE)")
    String judgmentResult,

    @Schema(description = "판결 점수 (0-100)")
    Integer judgmentScore,

    @Schema(description = "경험치 변화")
    Integer expChange,

    @Schema(description = "상태")
    String status,

    @Schema(description = "생성 시간")
    LocalDateTime createdAt

) {
    /**
     * 영수증 정보
     */
    @Schema(description = "영수증 정보")
    public record ReceiptInfo(
        Long amount,
        String storeName,
        String category,
        String categoryLabel,
        String date
    ) {}

    /**
     * AiConversation으로부터 응답 생성
     */
    public static AiHistoryResponse from(AiConversation conv) {
        SpendingCategory category = SpendingCategory.fromString(conv.getCategory());
        return new AiHistoryResponse(
            conv.getConversationId(),
            new ReceiptInfo(
                conv.getAmount(),
                conv.getStoreName(),
                conv.getCategory(),
                category.getLabel(),
                conv.getPaymentDate() != null ? conv.getPaymentDate().toString() : null
            ),
            conv.getJudgmentResult(),
            conv.getJudgmentScore(),
            conv.getExpChange(),
            conv.getStatus(),
            conv.getCreatedAt()
        );
    }
}
