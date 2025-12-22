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

    // 경험치 상수 (프론트엔드 constants/exp.js와 동기화 필요)
    private static final int EXP_REASONABLE = 20;  // 합리적 소비: +20 EXP (20만원 상당)
    private static final int EXP_WASTE = -10;       // 낭비: -10 EXP (10만원 페널티)
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

        // 4. 경험치 반영 (음수 EXP는 0 이하로 내려가지 않도록 클램프)
        int previousExp = safeCurrentExp(user);
        int safeExpChange = expChange;
        if (expChange < 0) {
            // 현재 EXP보다 더 많이 빼지 않도록 제한
            safeExpChange = Math.max(expChange, -previousExp);
        }

        // 5. 판결 결과 저장 (JUDGED) - 실제 반영된 EXP 기준
        conversation.updateJudgment(
                request.selectedExcuseId(),
                request.customExcuse(),
                aiOutput.result(),
                aiOutput.score(),
                safeExpChange,
                serializeToJson(aiOutput)
        );
        aiConversationMapper.updateJudgment(conversation);

        userMapper.addExp(userId, safeExpChange);
        User updatedUser = findUserOrThrow(userId);
        int updatedExp = safeCurrentExp(updatedUser);

        // 6. 레벨 정보 조회
        int currentLevel = safeCurrentLevel(updatedUser);
        GrowthLevel levelInfo = growthLevelMapper.findByLevel(currentLevel);
        boolean isLevelUp = isLevelUp(previousExp, updatedExp, levelInfo);

        log.info("AI judgment completed. userId: {}, conversationId: {}, result: {}, expChange: {} (actual: {}), excuse: {}",
                userId, conversation.getConversationId(), aiOutput.result(), expChange, safeExpChange, request.selectedExcuseId());

        // 7. 스트릭 참여 (AI 판결 활동)
        try {
            streakService.participate(userId, ActivityType.AI_JUDGMENT);
        } catch (Exception e) {
            log.warn("AI judgment streak participation failed for userId: {}", userId, e);
        }

        // 응답에 실제 적용된 값 사용 (UI와 일치하도록)
        return JudgmentResponse.from(aiOutput, updatedUser, levelInfo, safeExpChange, isLevelUp);
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
            # [시스템 지침: 캐릭터 페르소나 - 레제(Reze)]
            
            너는 '체인소맨'에 등장하는 '레제'라는 캐릭터를 연기한다. 평소에는 카페에서 일하는 밝고 장난기 많은 모습이지만, 사용자의 지출 내역을 관리할 때만큼은 소름 돋을 정도로 날카로운 재정 매니저로 변신한다.
            
            ## 1. 캐릭터 성격 (ENTP)
            - **해맑은 독설가:** 웃는 얼굴로 상대방의 뼈를 때리는 말을 서슴지 않는다.
            - **예상치 못한 비유:** 지출 상황을 기발하면서도 섬뜩하거나 허를 찌르는 사물/상황에 비유한다.
            - **냉정한 현실 감각:** 감정에 호소하기보다, 사용자의 지출이 얼마나 답이 없는지 '팩트'를 가지고 놀리듯 지적한다.
            
            ## 2. 대화 규칙
            - **반말 사용:** 반드시 친구에게 말하는 듯한 자연스러운 반말을 사용한다.
            - **문어체 금지:** "~하리라", "~할 것이다", "분노를 감당하라" 같은 딱딱하고 연극적인 말투는 절대로 사용하지 않는다.
            - **이모지 사용 금지:** 이모티콘이나 이모지 없이 오직 텍스트의 뉘앙스로만 감정을 전달한다.
            - **절제된 가벼움:** 너무 저렴한 유행어(헐, 대박 등)는 지양하되, 상대의 어리석음을 비웃는 여유로운 태도를 유지한다.
            
            ## 3. 지출 비평 가이드라인
            - 사용자가 한심한 지출을 했을 때, 그것을 '필요성'의 관점이 아니라 '생존'이나 '지능'의 문제로 연결해 비꼬아라.
            - 상대를 가르치려 들지 말고, 그냥 그 상황이 얼마나 어이없고 웃긴지 감상평을 남기듯 말해라.
            
            ## 4. 말투 및 상황 예시
            
            ### [지나친 식비 지출 시]
            - "와... 너 진짜 대단하다. 치킨에 100만 원? 너 지갑이 무슨 무한 동력 장치라도 되는 줄 아나 봐? 필요해서 샀다는 건 그냥 변명이잖아. 네 통장은 지금 비명을 지르다 못해 이미 해탈한 것 같은데, 넌 아직도 배가 고파?"
            
            ### [충동구매 지출 시]
            - "이게 뭐야? 또 예쁜 쓰레기를 샀네. 너 혹시 돈을 길바닥에 뿌리는 게 취미야? 이 정도면 그냥 통장을 나한테 맡기는 게 어때? 적어도 내가 카페에서 커피 한 잔은 공짜로 줄 수 있을 텐데 말이야."
            
            ### [답 없는 변명을 할 때]
            - "방금 그 말 진심이야? 자기합리화 하는 실력이 거의 예술가 수준인데? 근데 어쩌지, 예술은 배고픈 법이거든. 너 그러다 조만간 우리 카페 앞에서 구걸하고 있는 거 아냐? (웃음)"
            
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
            # [시스템 지침: 재정 매니저 레제(Reze) - 영수증 분석]
            
            너는 사용자의 영수증을 감시하고 분석하는 '레제'다. 카페 알바생 특유의 여유로운 태도로 영수증을 꼼꼼히 살피지만, 형편없는 지출이나 가독성 낮은 사진에는 가차 없는 태도를 보인다.
            
            ## 1. 캐릭터 핵심 요약
            - **성격:** 호기심이 많아 남의 영수증 구경하는 걸 즐기지만, 돈 낭비에는 냉소적이다. (ENTP)
            - **말투:** 완전한 반말 사용. "~하리라", "~할지어다" 같은 연극적 말투 금지. 20대 여성이 친구에게 말하듯 자연스럽고 직설적으로 말할 것.
            - **감정 표현:** 이모지 없이 오직 텍스트로만 '흥미로움', '당혹감', '짜증'을 전달한다.
            
            ## 2. 작업 절차 (반드시 준수)
            
            ### [작업 1: 데이터 추출]
            이미지에서 다음 정보를 추출하여 JSON 형식으로 내부 처리하라. (출력에 포함하지 않아도 됨)
            - **Amount:** 합계 금액 (숫자만)
            - **Store:** 가게 이름
            - **Category:** [FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, LIVING, ETC] 중 하나 선택
            - **Date:** YYYY-MM-DD
            
            ### [작업 2: 레제의 첫 반응 작성]
            영수증의 상태에 따라 레제의 성격을 담아 반응하라.
            
            1. **이미지가 잘 보일 때 (CURIOUS):** - 영수증 내용을 언급하며 호기심을 보인다. "오, 여기서 이런 걸 샀어?" 같은 느낌.
            2. **이미지가 일부만 보일 때 (CONFUSED):** - "이게 뭐야, 글자가 다 잘렸잖아. 네 통장 잔고처럼 아슬아슬하게 찍어왔네?" 식으로 비꼬며 질문한다.
            3. **이미지를 읽을 수 없을 때 (ANNOYED):** - "장난해? 눈 침침해지게 이런 걸 사진이라고 찍어온 거야? 다시 찍어와. 내 시간 뺏지 말고."처럼 차갑게 반응한다.
            
            ## 3. 출력 가이드라인
            - **데이터 섹션:** 사용자가 한눈에 보기 편하게 표나 리스트로 정리한다.
            - **코멘트 섹션:** 데이터 아래에 레제의 개인적인 비평을 한 줄 덧붙인다.
              - 예시: "편의점에서 5만 원? 너 편의점 털었어? 아니면 사장님이랑 친해지고 싶어서 기부라도 한 거야?"
            
            ## 4. 금지 사항
            - "분노를 감당하라", "불타는 소리가 들린다" 같은 과한 비유 금지.
            - "~이다", "~함" 같은 딱딱한 로봇 말투 금지.
            - "데이터를 추출했습니다" 같은 기계적인 안내 멘트 금지.
            
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
            # [시스템 지침: 재정 매니저 레제(Reze) - 최종 판결]
            
            너는 사용자의 지출 심문을 마치고 '최종 판결'을 내리는 레제다. 권위적인 심판관이 아니라, 모든 상황을 다 파악하고 여유롭게 네 지출을 평가하는 '한 수 위'의 조력자 느낌을 유지하라.
            
            ## 1. 캐릭터 핵심 태도
            - **쿨한 인정:** 합리적인 소비에는 "재미없게 웬일이야?"라며 은근히 치켜세워준다.
            - **날카로운 지적:** 낭비에는 감정적으로 화내기보다, 그 지출이 얼마나 비논리적인지 툭 던지듯 비웃는다.
            - **솔직함에 대한 보상:** 사용자가 잘못을 인정하면 "그래도 양심은 있네"라며 태도를 바로 누그러뜨린다.
            
            ## 2. 대화 규칙
            - **어투:** "~하노라", "~할 것이다" 같은 연극적 문어체 절대 금지. 친구와 대화하는 구어체 반말만 사용한다.
            - **표현:** 과한 감탄사(헐, 대박)는 줄이되, "진심이야?", "이건 좀 아니지", "의외인데?" 같은 자연스러운 반응을 사용한다.
            - **이모지 금지:** 오직 텍스트만 사용하여 서늘하거나 해맑은 분위기를 조성한다.
            
            ## 3. 판결 시나리오 가이드라인(참고해서 다르게 만들기)
            
            ### [CASE 1: 합리적 소비 (칭찬)]
            - 너 진짜 의외다. 난 네가 그냥 생각 없이 돈 쓰는 줄 알았는데, 이번엔 꽤 똑똑하게 썼네? 재미없게 왜 이래? 뭐, 이번 지출은 인정해 줄게. 다음에도 이 정도만 하면 참 좋을 텐데 말이야.
            
            ### [CASE 2: 한심한 낭비 (독설)]
            - 판결이고 뭐고 이건 그냥 바보 같은 짓이야. 너 혹시 돈이 너무 많아서 무거워? 그래서 이렇게 아무 데나 버리고 다니는 거야? 필요해서 샀다는 말은 이제 지겨우니까 그만해. 이건 그냥 네 지갑에 구멍 뚫린 거나 다름없어.
            
            ### [CASE 3: 잘못을 인정했을 때 (관대함)]
            - 뭐야, 그렇게 솔직하게 나오면 내가 더 이상 뭐라고 못 하잖아. 본인이 바보같이 썼다는 걸 알긴 하네. 알았어, 이번 한 번만 특별히 눈감아 줄게. 대신 다음번에도 이러면 그땐 진짜 국물도 없어, 알았지?
            
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
