package com.jipjung.project.controller.dto.response;

import com.jipjung.project.ai.dto.AiAnalysisOutput;
import com.jipjung.project.domain.AiConversation;
import com.jipjung.project.domain.ExtractionStatus;
import com.jipjung.project.domain.SpendingCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 지출 분석 응답 DTO
 * <p>
 * 입력 모드에 따라 응답 구조가 다릅니다:
 * <ul>
 *   <li>MANUAL: status=ANALYZED, receiptInfo + persona + suggestedExcuses 포함</li>
 *   <li>IMAGE: status=EXTRACTING, extractionStatus + extractedData + missingFields 포함</li>
 * </ul>
 */
@Schema(description = "지출 분석 응답")
public record SpendingAnalyzeResponse(

    @Schema(description = "대화 ID")
    Long conversationId,

    @Schema(description = "대화 상태", example = "ANALYZED")
    String status,

    @Schema(description = "추출 상태 (IMAGE 모드)", example = "COMPLETE")
    ExtractionStatus extractionStatus,

    @Schema(description = "추출된 데이터 (IMAGE 모드)")
    ExtractedData extractedData,

    @Schema(description = "누락된 필드 목록 (PARTIAL/FAILED 시)")
    List<String> missingFields,

    @Schema(description = "영수증 정보 (MANUAL/confirm 후)")
    ReceiptInfo receiptInfo,

    @Schema(description = "AI 페르소나 반응")
    Persona persona,

    @Schema(description = "변명 선택지 (MANUAL/confirm 후)")
    List<SuggestedExcuse> suggestedExcuses

) {
    // =========================================================================
    // Nested Records
    // =========================================================================

    /**
     * 추출된 데이터 (IMAGE 모드에서 AI가 영수증에서 추출한 정보)
     */
    @Schema(description = "추출된 데이터")
    public record ExtractedData(
        @Schema(description = "금액", example = "31000")
        Long amount,
        @Schema(description = "가게명", example = "치킨플러스 강남점")
        String storeName,
        @Schema(description = "카테고리", example = "FOOD")
        String category,
        @Schema(description = "결제일", example = "2025-12-10")
        String paymentDate
    ) {}

    /**
     * 영수증 정보 (분석 완료 후 표시)
     */
    @Schema(description = "영수증 정보")
    public record ReceiptInfo(
        Long amount,
        String storeName,
        String categoryLabel,
        String paymentDate
    ) {
        public static ReceiptInfo from(AiConversation conv) {
            return new ReceiptInfo(
                conv.getAmount(),
                conv.getStoreName(),
                SpendingCategory.fromString(conv.getCategory()).getLabel(),
                conv.getPaymentDate().toString()
            );
        }
    }

    /**
     * AI 페르소나
     */
    @Schema(description = "AI 페르소나")
    public record Persona(
        String mood,
        String moodLabel,
        String script
    ) {}

    /**
     * 변명 선택지
     */
    @Schema(description = "변명 선택지")
    public record SuggestedExcuse(
        String id,
        String text,
        String type
    ) {}

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * MANUAL 모드 분석 결과로부터 응답 생성
     */
    public static SpendingAnalyzeResponse fromManual(AiConversation conv, AiAnalysisOutput aiOutput) {
        return new SpendingAnalyzeResponse(
            conv.getConversationId(),
            conv.getStatus(),
            null,  // extractionStatus
            null,  // extractedData
            null,  // missingFields
            ReceiptInfo.from(conv),
            new Persona(aiOutput.mood(), aiOutput.moodLabel(), aiOutput.script()),
            aiOutput.suggestedExcuses().stream()
                .map(e -> new SuggestedExcuse(e.id(), e.text(), e.type()))
                .toList()
        );
    }

    /**
     * IMAGE 모드 추출 결과로부터 응답 생성
     */
    public static SpendingAnalyzeResponse fromImageExtraction(
            AiConversation conv,
            AiAnalysisOutput aiOutput,
            ExtractionStatus extractionStatus,
            List<String> missingFields
    ) {
        ExtractedData extracted = null;
        if (extractionStatus != ExtractionStatus.FAILED) {
            extracted = new ExtractedData(
                aiOutput.extractedAmount(),
                aiOutput.extractedStoreName(),
                aiOutput.extractedCategory(),
                aiOutput.extractedPaymentDate()
            );
        }

        return new SpendingAnalyzeResponse(
            conv.getConversationId(),
            conv.getStatus(),
            extractionStatus,
            extracted,
            missingFields,
            null,  // receiptInfo (confirm 후 제공)
            new Persona(aiOutput.mood(), aiOutput.moodLabel(), aiOutput.script()),
            null   // suggestedExcuses (confirm 후 제공)
        );
    }

    /**
     * 기존 호환용 팩토리 메서드 (deprecated, fromManual 사용 권장)
     */
    public static SpendingAnalyzeResponse from(AiConversation conv, AiAnalysisOutput aiOutput) {
        return fromManual(conv, aiOutput);
    }
}
