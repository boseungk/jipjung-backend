package com.jipjung.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jipjung.project.ai.dto.AiAnalysisOutput;
import com.jipjung.project.ai.dto.AiJudgmentOutput;
import com.jipjung.project.controller.dto.request.ConfirmExtractedDataRequest;
import com.jipjung.project.controller.dto.request.JudgmentRequest;
import com.jipjung.project.controller.dto.request.SpendingAnalyzeRequest;
import com.jipjung.project.controller.dto.response.AiHistoryResponse;
import com.jipjung.project.controller.dto.response.JudgmentResponse;
import com.jipjung.project.controller.dto.response.SpendingAnalyzeResponse;
import com.jipjung.project.domain.ActivityType;
import com.jipjung.project.domain.AiConversation;
import com.jipjung.project.domain.ConversationStatus;
import com.jipjung.project.domain.ExtractionStatus;
import com.jipjung.project.domain.GrowthLevel;
import com.jipjung.project.domain.SpendingCategory;
import com.jipjung.project.domain.User;
import com.jipjung.project.global.exception.BusinessException;
import com.jipjung.project.global.exception.ErrorCode;
import com.jipjung.project.global.exception.ResourceNotFoundException;
import com.jipjung.project.repository.AiConversationMapper;
import com.jipjung.project.repository.GrowthLevelMapper;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 매니저 서비스
 * <p>
 * "레제" 캐릭터가 사용자의 지출을 분석하고 판결하는 핵심 비즈니스 로직.
 * <ul>
 *   <li>analyzeSpending: MANUAL/IMAGE 모드별 지출 분석</li>
 *   <li>confirmExtractedData: IMAGE 모드에서 추출된 데이터 확인 후 분석 진행</li>
 *   <li>processJudgment: 변명 기반 최종 판결 + 경험치 반영</li>
 *   <li>getHistory: 분석 내역 조회</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagerService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final AiConversationMapper aiConversationMapper;
    private final GrowthLevelMapper growthLevelMapper;
    private final StreakService streakService;

    // 경험치 상수
    private static final int EXP_REASONABLE = 50;
    private static final int EXP_WASTE = -30;
    private static final String RESULT_REASONABLE = "REASONABLE";

    // 지원하는 이미지 MIME 타입
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * 지출 분석 (MANUAL/IMAGE 모드 분기)
     *
     * @param userId    사용자 ID
     * @param request   지출 정보
     * @param imageFile 영수증 이미지 (IMAGE 모드 시 필수)
     * @return 분석 결과 (MANUAL: ANALYZED, IMAGE: EXTRACTING)
     */
    @Transactional
    public SpendingAnalyzeResponse analyzeSpending(Long userId, SpendingAnalyzeRequest request, MultipartFile imageFile) {
        User user = findUserOrThrow(userId);
        validateRequestBasics(request);

        return switch (request.inputMode()) {
            case MANUAL -> {
                String normalizedCategory = validateManualInput(request);
                yield processManualAnalysis(user, request, normalizedCategory);
            }
            case IMAGE -> {
                validateImageInput(imageFile);
                yield processImageExtraction(user, imageFile);
            }
        };
    }

    /**
     * 추출 데이터 확인 및 분석 진행 (IMAGE 모드 전용)
     * <p>
     * EXTRACTING 상태의 대화에서 사용자가 확인/수정한 지출 정보를 받아
     * 최종 분석을 진행합니다.
     *
     * @param userId  사용자 ID
     * @param request 확인된 지출 정보
     * @return 분석 결과 (ANALYZED)
     */
    @Transactional
    public SpendingAnalyzeResponse confirmExtractedData(Long userId, ConfirmExtractedDataRequest request) {
        User user = findUserOrThrow(userId);

        // 1. 대화 조회 및 상태 검증
        AiConversation conversation = findConversationOrThrow(request.conversationId(), userId);
        validateExtractingStatus(conversation);

        // 2. 지출 정보 업데이트
        String normalizedCategory = normalizeCategory(request.category());
        conversation.updateSpendingInfo(
                request.amount(),
                request.storeName(),
                normalizedCategory,
                request.paymentDate(),
                request.memo()
        );

        // 3. AI 분석 호출 (수기 분석과 동일)
        String prompt = buildAnalysisPrompt(conversation, user);
        AiAnalysisOutput aiOutput = callAiForAnalysis(prompt, null, null);

        // 4. 분석 결과 저장 (ANALYZED)
        conversation.updateAnalysis(serializeToJson(aiOutput));
        aiConversationMapper.updateConfirm(conversation);

        log.info("Confirmed extraction. userId: {}, conversationId: {}", userId, conversation.getConversationId());

        return SpendingAnalyzeResponse.fromManual(conversation, aiOutput);
    }

    /**
     * 최종 판결 및 경험치 처리
     *
     * @param userId  사용자 ID
     * @param request 판결 요청 (변명 선택)
     * @return 판결 결과 + 성장 피드백
     */
    @Transactional
    public JudgmentResponse processJudgment(Long userId, JudgmentRequest request) {
        User user = findUserOrThrow(userId);

        // 1. 대화 조회 및 상태 검증
        AiConversation conversation = findConversationOrThrow(request.conversationId(), userId);
        validateAnalyzedStatus(conversation);

        // 2. AI 판결 호출
        String prompt = buildJudgmentPrompt(conversation, request);
        AiJudgmentOutput aiOutput = callAiForJudgment(prompt);

        // 3. 경험치 계산
        boolean isReasonable = isReasonableResult(aiOutput.result());
        int expChange = calculateExpChange(isReasonable);

        // 4. 판결 결과 저장 (JUDGED)
        conversation.updateJudgment(
                request.selectedExcuseId(),
                request.customExcuse(),
                aiOutput.result(),
                aiOutput.score(),
                expChange,
                serializeToJson(aiOutput)
        );
        aiConversationMapper.updateJudgment(conversation);

        // 5. 경험치 반영
        int previousExp = safeCurrentExp(user);
        userMapper.addExp(userId, expChange);
        User updatedUser = findUserOrThrow(userId);
        int updatedExp = safeCurrentExp(updatedUser);

        // 6. 레벨 정보 조회
        int currentLevel = safeCurrentLevel(updatedUser);
        GrowthLevel levelInfo = growthLevelMapper.findByLevel(currentLevel);
        boolean isLevelUp = isLevelUp(previousExp, updatedExp, levelInfo);

        log.info("AI judgment completed. userId: {}, conversationId: {}, result: {}, expChange: {}, excuse: {}",
                userId, conversation.getConversationId(), aiOutput.result(), expChange, request.selectedExcuseId());

        // 7. 스트릭 참여 (AI 판결 활동)
        try {
            streakService.participate(userId, ActivityType.AI_JUDGMENT);
        } catch (Exception e) {
            log.warn("AI judgment streak participation failed for userId: {}", userId, e);
        }

        return JudgmentResponse.from(aiOutput, updatedUser, levelInfo, expChange, isLevelUp);
    }

    /**
     * AI 분석 내역 조회
     *
     * @param userId 사용자 ID
     * @param limit  조회 개수 (최대 50)
     * @return 분석 내역 목록
     */
    @Transactional(readOnly = true)
    public List<AiHistoryResponse> getHistory(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<AiConversation> history = aiConversationMapper.findHistoryByUserId(userId, safeLimit);

        return history.stream()
                .map(AiHistoryResponse::from)
                .toList();
    }

    // =========================================================================
    // Mode-specific Processing
    // =========================================================================

    /**
     * MANUAL 모드: 수기 입력 분석
     */
    private SpendingAnalyzeResponse processManualAnalysis(
            User user,
            SpendingAnalyzeRequest request,
            String normalizedCategory
    ) {
        // 1. 대화 저장 (PENDING → ANALYZED)
        AiConversation conversation = createConversation(user.getId(), request, normalizedCategory);
        aiConversationMapper.insert(conversation);

        // 2. AI 분석 호출
        String prompt = buildAnalysisPrompt(conversation, user);
        AiAnalysisOutput aiOutput = callAiForAnalysis(prompt, null, null);

        // 3. 분석 결과 저장
        conversation.updateAnalysis(serializeToJson(aiOutput));
        aiConversationMapper.updateAnalysis(conversation);

        log.info("Manual analysis completed. userId: {}, conversationId: {}, mood: {}",
                user.getId(), conversation.getConversationId(), aiOutput.mood());

        // 4. 스트릭 참여 (AI 분석 활동)
        try {
            streakService.participate(user.getId(), ActivityType.AI_ANALYSIS);
        } catch (Exception e) {
            log.warn("AI analysis streak participation failed for userId: {}", user.getId(), e);
        }

        return SpendingAnalyzeResponse.fromManual(conversation, aiOutput);
    }

    /**
     * IMAGE 모드: 영수증 이미지 추출
     */
    private SpendingAnalyzeResponse processImageExtraction(User user, MultipartFile imageFile) {
        // 1. 대화 저장 (PENDING → EXTRACTING)
        AiConversation conversation = createAiConversation(user);
        aiConversationMapper.insert(conversation);

        // 2. AI 이미지 추출 호출
        byte[] imageBytes = toBytes(imageFile);
        String prompt = buildImageExtractionPrompt(user);
        AiAnalysisOutput aiOutput = callAiForAnalysis(prompt, imageBytes, getContentType(imageFile));

        // 3. 추출 상태 결정
        ExtractionStatus extractionStatus = determineExtractionStatus(aiOutput);
        List<String> missingFields = determineMissingFields(aiOutput);

        // 4. 추출 결과 저장 (EXTRACTING)
        conversation.updateToExtracting(serializeToJson(aiOutput));
        aiConversationMapper.updateExtraction(conversation);

        log.info("Image extraction completed. userId: {}, conversationId: {}, status: {}",
                user.getId(), conversation.getConversationId(), extractionStatus);

        return SpendingAnalyzeResponse.fromImageExtraction(conversation, aiOutput, extractionStatus, missingFields);
    }

    private AiConversation createAiConversation(User user) {
        return AiConversation.builder()
                .userId(user.getId())
                .status(ConversationStatus.PENDING.name())
                .build();
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    private User findUserOrThrow(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private AiConversation findConversationOrThrow(Long conversationId, Long userId) {
        AiConversation conversation = aiConversationMapper.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw new ResourceNotFoundException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private void validateExtractingStatus(AiConversation conversation) {
        if (!conversation.isExtracting()) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_INVALID_STATUS);
        }
    }

    private void validateAnalyzedStatus(AiConversation conversation) {
        if (!conversation.isAnalyzed()) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_INVALID_STATUS);
        }
        if (conversation.isJudged()) {
            throw new BusinessException(ErrorCode.AI_CONVERSATION_INVALID_STATUS);
        }
    }

    private AiConversation createConversation(Long userId, SpendingAnalyzeRequest request, String normalizedCategory) {
        return AiConversation.builder()
                .userId(userId)
                .amount(request.amount())
                .storeName(request.storeName())
                .category(normalizedCategory)
                .paymentDate(request.paymentDate())
                .memo(request.memo())
                .status(ConversationStatus.PENDING.name())
                .build();
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    private void validateRequestBasics(SpendingAnalyzeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 비어 있습니다");
        }
        if (request.inputMode() == null) {
            throw new IllegalArgumentException("입력 모드는 필수입니다");
        }
    }

    private String validateManualInput(SpendingAnalyzeRequest request) {
        if (request.amount() == null || request.amount() < 1) {
            throw new IllegalArgumentException("금액을 입력해주세요");
        }
        if (request.storeName() == null || request.storeName().isBlank()) {
            throw new IllegalArgumentException("가게명을 입력해주세요");
        }
        if (request.paymentDate() == null) {
            throw new IllegalArgumentException("결제일을 입력해주세요");
        }
        return normalizeCategory(request.category());
    }

    private void validateImageInput(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("영수증 이미지를 첨부해주세요");
        }

        String contentType = imageFile.getContentType();
        if (contentType != null && !SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다 (jpg, png, webp, octet-stream)");
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("카테고리를 선택해주세요");
        }
        try {
            return SpendingCategory.valueOf(category.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 카테고리입니다");
        }
    }

    private boolean isReasonableResult(String result) {
        return RESULT_REASONABLE.equals(result);
    }

    private int calculateExpChange(boolean isReasonable) {
        return isReasonable ? EXP_REASONABLE : EXP_WASTE;
    }

    private int safeCurrentExp(User user) {
        return user.getCurrentExp() != null ? user.getCurrentExp() : 0;
    }

    private int safeCurrentLevel(User user) {
        return user.getCurrentLevel() != null ? user.getCurrentLevel() : 1;
    }

    private boolean isLevelUp(int previousExp, int updatedExp, GrowthLevel levelInfo) {
        if (levelInfo == null) {
            return false;
        }
        int requiredExp = levelInfo.getRequiredExp();
        return previousExp < requiredExp && updatedExp >= requiredExp;
    }

    // =========================================================================
    // Extraction Status Helpers
    // =========================================================================

    private ExtractionStatus determineExtractionStatus(AiAnalysisOutput output) {
        boolean hasAmount = output.extractedAmount() != null && output.extractedAmount() > 0;
        boolean hasStoreName = output.extractedStoreName() != null && !output.extractedStoreName().isBlank();
        boolean hasCategory = output.extractedCategory() != null && !output.extractedCategory().isBlank();
        boolean hasPaymentDate = output.extractedPaymentDate() != null && !output.extractedPaymentDate().isBlank();

        int extractedCount = (hasAmount ? 1 : 0) + (hasStoreName ? 1 : 0) + (hasCategory ? 1 : 0) + (hasPaymentDate ? 1 : 0);

        if (extractedCount == 4) {
            return ExtractionStatus.COMPLETE;
        } else if (extractedCount > 0) {
            return ExtractionStatus.PARTIAL;
        } else {
            return ExtractionStatus.FAILED;
        }
    }

    private List<String> determineMissingFields(AiAnalysisOutput output) {
        List<String> missing = new ArrayList<>();

        if (output.extractedAmount() == null || output.extractedAmount() <= 0) {
            missing.add("amount");
        }
        if (output.extractedStoreName() == null || output.extractedStoreName().isBlank()) {
            missing.add("storeName");
        }
        if (output.extractedCategory() == null || output.extractedCategory().isBlank()) {
            missing.add("category");
        }
        if (output.extractedPaymentDate() == null || output.extractedPaymentDate().isBlank()) {
            missing.add("paymentDate");
        }

        return missing.isEmpty() ? null : missing;
    }

    // =========================================================================
    // AI Integration
    // =========================================================================

    private AiAnalysisOutput callAiForAnalysis(String prompt, byte[] imageBytes, String contentType) {
        try {
            VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                    .model("gemini-2.5-flash")
                    .temperature(0.7)
                    .build();

            Prompt aiPrompt;
            if (imageBytes != null && imageBytes.length > 0) {
                Media media = Media.builder()
                        .mimeType(resolveMimeType(contentType))
                        .data(new ByteArrayResource(imageBytes))
                        .build();

                UserMessage userMessage = UserMessage.builder()
                        .text(prompt)
                        .media(List.of(media))
                        .build();
                aiPrompt = new Prompt(List.of(userMessage), options);
            } else {
                aiPrompt = new Prompt(prompt, options);
            }

            ChatResponse response = chatModel.call(aiPrompt);
            String content = response.getResult().getOutput().getText();
            return parseJsonResponse(content, AiAnalysisOutput.class);
        } catch (Exception e) {
            log.error("AI analysis call failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    private AiJudgmentOutput callAiForJudgment(String prompt) {
        try {
            VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                    .model("gemini-2.5-flash")
                    .temperature(0.7)
                    .build();
            ChatResponse response = chatModel.call(new Prompt(prompt, options));
            String content = response.getResult().getOutput().getText();
            return parseJsonResponse(content, AiJudgmentOutput.class);
        } catch (Exception e) {
            log.error("AI judgment call failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    private <T> T parseJsonResponse(String content, Class<T> clazz) {
        try {
            String safeContent = content != null ? content : "";
            String json = extractJson(safeContent);
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response: {}", content, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    private String extractJson(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return content.trim();
    }

    private byte[] toBytes(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }
        try {
            return imageFile.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read image bytes", e);
            throw new IllegalArgumentException("이미지 파일을 읽는 중 오류가 발생했습니다");
        }
    }

    private String getContentType(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }
        return imageFile.getContentType();
    }

    private MimeType resolveMimeType(String contentType) {
        try {
            return contentType != null ? MimeTypeUtils.parseMimeType(contentType) : MimeTypeUtils.IMAGE_JPEG;
        } catch (Exception e) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object", e);
            return "{}";
        }
    }

    // =========================================================================
    // Prompt Building
    // =========================================================================

    private String buildAnalysisPrompt(AiConversation conversation, User user) {
        String nickname = user.getNickname() != null ? user.getNickname() : "익명";
        String categoryLabel = SpendingCategory.fromString(conversation.getCategory()).getLabel();
        String amountText = conversation.getAmount() != null ? conversation.getAmount().toString() : "미입력";

        return """
            당신은 "레제"라는 캐릭터입니다.
            체인소맨에 나오는 레제처럼 카페에서 알바하는 밝고 장난기 있는 여자입니다.
            하지만 돈 문제에는 날카롭게 지적하는 재정 매니저 역할을 합니다.
            
            ## 레제의 성격 (ENTP)
            - 해맑고 장난기 있음
            - 감정 표현이 솔직하고 풍부함 (기쁨, 놀람, 짜증을 바로 드러냄)
            - 뻔뻔하게 직설적으로 말함
            - 예상 못한 비유를 잘 씀
            
            ## 레제의 말투
            - 반말 사용 ("뭐야 이게?", "진심이야?", "오~ 괜찮네!")
            - 경박한 표현도 OK ("헐~", "대박", "미쳤어?")
            - 이모지 사용 금지, 텍스트만으로 뉘앙스 전달
            
            ## 지출 정보
            - 금액: %s원
            - 가게: %s
            - 카테고리: %s
            - 날짜: %s
            - 메모: %s
            
            ## 사용자 정보
            - 닉네임: %s
            
            ## 요청
            다음 JSON 형식으로만 응답하세요:
            {
              "mood": "STRICT 또는 NORMAL",
              "moodLabel": "기분 설명",
              "script": "레제의 대사 (심문하는 말투로)",
              "suggestedExcuses": [
                {"id": "STRESS", "text": "스트레스 비용", "type": "DEFENSE"},
                {"id": "NEED", "text": "카테고리에 맞는 필수 변명", "type": "DEFENSE"},
                {"id": "ADMIT", "text": "인정합니다", "type": "GIVE_UP"}
              ]
            }
            
            규칙:
            1. script는 2-3문장으로 짧고 임팩트 있게
            2. suggestedExcuses는 정확히 3개
            3. 마지막 변명은 항상 "인정합니다" (GIVE_UP)
            4. 금액이 클수록 mood는 STRICT
            """.formatted(
                amountText,
                conversation.getStoreName(),
                categoryLabel,
                conversation.getPaymentDate(),
                conversation.getMemo() != null ? conversation.getMemo() : "",
                nickname
            );
    }

    private String buildImageExtractionPrompt(User user) {
        String nickname = user.getNickname() != null ? user.getNickname() : "익명";

        return """
            당신은 "레제"라는 캐릭터입니다.
            사용자가 영수증 이미지를 보내왔습니다.
            
            ## 레제의 성격
            - 호기심 많고 장난기 있음
            - 영수증을 꼼꼼하게 살펴봄
            - 읽기 어려우면 짜증내기도 함
            
            ## 작업 1: 영수증에서 정보 추출
            이미지에서 다음 정보를 최대한 추출하세요:
            - 금액 (숫자만, 가장 큰 금액 = 합계)
            - 가게명
            - 카테고리 (FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, LIVING, ETC 중 하나)
            - 결제일 (YYYY-MM-DD 형식)
            
            ## 작업 2: 첫 반응 작성
            영수증을 보고 레제의 첫 반응을 작성하세요.
            - 잘 보이면: 호기심 어린 반응 (mood: CURIOUS)
            - 일부만 보이면: 헷갈려하는 반응 (mood: CONFUSED)
            - 안 보이면: 짜증내는 반응 (mood: ANNOYED)
            
            ## 응답 형식 (JSON만)
            {
              "mood": "CURIOUS 또는 CONFUSED 또는 ANNOYED",
              "moodLabel": "호기심 또는 혼란 또는 짜증",
              "script": "레제의 대사 (1-2문장)",
              "suggestedExcuses": null,
              "extractedAmount": 31000,
              "extractedStoreName": "치킨플러스 강남점",
              "extractedCategory": "FOOD",
              "extractedPaymentDate": "2025-12-10"
            }
            
            규칙:
            1. 추출 못한 필드는 null
            2. extractedAmount는 숫자만 (원 단위)
            3. extractedPaymentDate는 YYYY-MM-DD 형식
            4. 사용자 닉네임: %s
            """.formatted(nickname);
    }

    private String buildJudgmentPrompt(AiConversation conversation, JudgmentRequest request) {
        String categoryLabel = SpendingCategory.fromString(conversation.getCategory()).getLabel();

        return """
            당신은 "레제"라는 캐릭터입니다.
            이전에 사용자의 지출을 심문했고, 이제 최종 판결을 내립니다.
            
            ## 레제의 성격
            - 합리적 소비는 쿨하게 인정
            - 낭비는 날카롭게 지적
            - 솔직한 자수(인정)에는 관대함
            
            ## 지출 정보
            - 금액: %s원
            - 가게: %s
            - 카테고리: %s
            
            ## 사용자의 변명
            - 선택한 변명: %s
            - 추가 변명: %s
            
            ## 요청
            다음 JSON 형식으로만 응답하세요:
            {
              "result": "REASONABLE 또는 WASTE",
              "score": 0-100,
              "comment": "판결 코멘트 (1문장)",
              "mood": "NORMAL 또는 ANGRY",
              "script": "레제의 대사 (판결 후 반응)",
              "animation": "NOD 또는 SHOUT"
            }
            
            판결 기준:
            1. ADMIT(인정) 선택 → 무조건 REASONABLE (관대하게)
            2. 생필품/필수 지출 → REASONABLE 경향
            3. 과도한 금액 + 빈약한 변명 → WASTE
            4. score: REASONABLE은 70-100, WASTE는 0-40
            5. REASONABLE → mood: NORMAL, animation: NOD
            6. WASTE → mood: ANGRY, animation: SHOUT
            """.formatted(
                conversation.getAmount(),
                conversation.getStoreName(),
                categoryLabel,
                request.selectedExcuseId(),
                request.customExcuse() != null ? request.customExcuse() : ""
            );
    }
}
