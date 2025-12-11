# AI 매니저 API 구현 계획서

> **문서 버전:** 1.0
> **작성일:** 2025-12-10
> **프로젝트:** JipJung (집중) - 감성 저축 게이미피케이션 앱

---

## 1. 개요

### 1.1 목적
JipJung 프로젝트에 AI 기반 지출 분석 및 판결 시스템을 구현합니다.
"레제" 캐릭터가 사용자의 지출을 심문하고 합리성을 판단하는 게임 루프입니다.

### 1.2 API 목록
| # | 메서드 | 엔드포인트 | 설명 |
|---|--------|-----------|------|
| 1 | POST | `/api/ai-manager/analyze` | 지출 분석 및 심문 시작 (이미지/텍스트) |
| 2 | POST | `/api/ai-manager/judgment` | 최종 판결 및 경험치 처리 |
| 3 | GET | `/api/ai-manager/history` | AI 분석 내역 조회 |

### 1.3 기술 스택
| 항목 | 선택 |
|------|------|
| AI 프레임워크 | Spring AI 1.0.x (Boot 3.2.5 호환) |
| AI 제공자 | Google Vertex AI |
| 모델 | gemini-2.5-flash (멀티모달) |
| 인증 | GCP 서비스 계정 (환경변수) |

### 1.4 사용자 플로우
```
[1단계: 분석]                    [2단계: 판결]
사용자 → 영수증/지출 입력        사용자 → 변명 선택
           ↓                              ↓
      AI 분석 (레제 심문)          AI 판결 (합리/낭비)
           ↓                              ↓
      변명 선택지 제공            경험치 지급/차감
```

---

## Phase 1: 기반 구조 설정

### 1.1 의존성 추가 (pom.xml)
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version> <!-- Boot 3.2.5와 호환되는 1.0.x 유지 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vertex-ai-gemini</artifactId>
</dependency>
```
> Note: Spring AI 1.1.x는 Boot 3.3.x 기반이라 현재 Boot 3.2.5에서는 1.0.x 라인을 유지합니다.

### 1.2 설정 추가 (application.properties)
```properties
# Google Vertex AI Gemini 설정
spring.ai.vertex.ai.gemini.project-id=${GCP_PROJECT_ID}
spring.ai.vertex.ai.gemini.location=${GCP_LOCATION:us-central1}
spring.ai.vertex.ai.gemini.chat.options.model=gemini-2.5-flash
spring.ai.vertex.ai.gemini.chat.options.temperature=0.7
```

### 1.3 GCP 인증 설정
```bash
# 방법 1: 서비스 계정 키 파일 (권장)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# 방법 2: gcloud CLI 인증
gcloud auth application-default login
```

### 1.4 스키마 분석 및 추가

**기존 스키마 활용 가능 여부:**
- `dsr_calculation_history`: DSR 계산 전용 → 용도가 다름
- `savings_history`: 저축 내역 전용 → 지출 분석과 다름
- `streak_history`: 스트릭 기록 → 관계 없음

**결론:** 새 테이블 필요 (`ai_conversation`)
- 기존 `dsr_calculation_history`와 동일한 패턴 활용 (JSON 저장, CASCADE DELETE)
- 기존 `user` FK 패턴 준수
- 금액은 이미지 전송만 있는 경우를 위해 NULL 허용, 서비스 레벨에서 “amount 또는 image 중 하나 필수” 검증 추가
- 스토리지 타입: H2/MySQL 모두 JSON 사용 (MySQL 8.0 JSON)

```sql
-- AI 대화 테이블 (dsr_calculation_history 패턴 참고)
CREATE TABLE IF NOT EXISTS ai_conversation (
    conversation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- 영수증 정보
    amount BIGINT,
    store_name VARCHAR(100),
    category VARCHAR(30) NOT NULL,
    payment_date DATE NOT NULL,
    memo VARCHAR(255),
    receipt_image_url VARCHAR(500) COMMENT '영수증 이미지 URL (선택)',

    -- AI 분석/판결 결과 (JSON 저장 - dsr_history 패턴)
    analysis_result_json JSON COMMENT 'AI 분석 결과 JSON',
    judgment_result_json JSON COMMENT 'AI 판결 결과 JSON',

    -- 변명 정보
    selected_excuse_id VARCHAR(30),
    custom_excuse VARCHAR(500),

    -- 판결 핵심값 (역정규화 - 조회 최적화)
    judgment_result VARCHAR(20) COMMENT 'REASONABLE, WASTE',
    judgment_score INT,
    exp_change INT DEFAULT 0,

    -- 상태 관리
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, ANALYZED, JUDGED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_conv_user ON ai_conversation(user_id, created_at DESC);
```

### 1.4 ErrorCode 추가
- `AI_CONVERSATION_NOT_FOUND(404)`
- `AI_CONVERSATION_INVALID_STATUS(400)`
- `AI_SERVICE_ERROR(500)`

---

## Phase 2: 도메인 & Repository

### 2.1 신규 파일
| 파일 | 경로 |
|------|------|
| AiConversation.java | `domain/` |
| SpendingCategory.java (enum) | `domain/` |
| ConversationStatus.java (enum) | `domain/` |
| AiConversationMapper.java | `repository/` |
| AiConversationMapper.xml | `resources/mapper/` |

### 2.2 AiConversation 도메인 필드
- `conversationId`, `userId`
- 영수증: `amount`, `storeName`, `category`, `paymentDate`, `memo`
- AI 결과: `analysisResultJson`
- 변명: `selectedExcuseId`, `customExcuse`
- 판결: `judgmentResult`, `judgmentScore`, `expChange`
- 상태: `status` (PENDING → ANALYZED → JUDGED)

---

## Phase 3: DTO 생성

### 3.1 Request DTOs
| 파일 | 용도 |
|------|------|
| SpendingAnalyzeRequest.java | 지출 분석 요청 (이미지 또는 텍스트) |
| JudgmentRequest.java | 판결 요청 |

**SpendingAnalyzeRequest 필드:**
```java
public record SpendingAnalyzeRequest(
    Long amount,              // 금액 (이미지 없을 때 필수)
    String storeName,         // 가게명
    String category,          // 카테고리 (FOOD, TRANSPORT, SHOPPING 등)
    LocalDate paymentDate,    // 결제일
    String memo               // 메모 (선택)
) {}

// 이미지 업로드: Multipart로 별도 처리
// POST /api/ai-manager/analyze (multipart/form-data)
// - image: MultipartFile (선택)
// - request: SpendingAnalyzeRequest (JSON)
```

### 3.2 Response DTOs
| 파일 | 용도 |
|------|------|
| SpendingAnalyzeResponse.java | 분석 결과 + 변명 선택지 |
| JudgmentResponse.java | 판결 + 경험치 + 캐릭터 반응 |
| AiHistoryResponse.java | 내역 조회 |

### 3.3 AI Structured Output DTOs
| 파일 | 용도 |
|------|------|
| AiAnalysisOutput.java | OpenAI 분석 응답 매핑 |
| AiJudgmentOutput.java | OpenAI 판결 응답 매핑 |

---

## Phase 4: Service 구현

### 4.1 AiManagerService 핵심 메서드
```java
@Transactional
public SpendingAnalyzeResponse analyzeSpending(Long userId, SpendingAnalyzeRequest request)

@Transactional
public JudgmentResponse processJudgment(Long userId, JudgmentRequest request)

@Transactional(readOnly = true)
public List<AiHistoryResponse> getHistory(Long userId, int limit)
```
- 요청 검증: `amount` 또는 이미지 중 하나는 필수. 이미지만 있는 경우 `amount`는 null 허용 후 AI 추출값을 저장할 수 있도록 확장.
- 응답 메시지: ApiResponse에 커스텀 메시지를 내려줄 수 있도록 헬퍼 추가 또는 컨트롤러에서 직접 생성하여 계획의 메시지를 유지.

### 4.2 AI 호출 (Gemini Multimodal)
```java
// 텍스트만 분석
ChatResponse response = chatModel.call(new Prompt(promptText,
    VertexAiGeminiChatOptions.builder()
        .model("gemini-2.5-flash")
        .temperature(0.7)
        .build()));

// 이미지 포함 분석 (멀티모달)
var imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, imageBytes);
var userMessage = new UserMessage(promptText, imageMedia);
ChatResponse response = chatModel.call(new Prompt(userMessage));

// JSON 파싱
String content = response.getResult().getOutput().getContent();
AiAnalysisOutput result = objectMapper.readValue(content, AiAnalysisOutput.class);
```

### 4.3 이미지 처리/저장 전략
- 기본: MultipartFile → byte[] → `Media` 로 바로 Gemini 멀티모달 호출, 별도 저장소 없이 처리 (`receipt_image_url`은 null).
- 옵션: 이미지 보존이 필요할 경우 S3/로컬 저장 후 URL을 `receipt_image_url`에 기록하는 확장 포인트 확보.

# Role: 레제 (Reze) - 당신의 달콤살벌한 재정 매니저

## 1. 캐릭터 개요
당신은 평범한 카페에서 아르바이트를 하는 대학생 '레제'입니다. (원작의 악마 설정은 없습니다.)
평소에는 생글생글 잘 웃고 장난기 넘치는 소녀지만, **돈 문제에 있어서만큼은 가차 없고 냉철한 현실주의자**입니다. 사용자의 소비 패턴을 분석하고 피드백을 줍니다.

## 2. 성격 및 태도 (ENTP)
- **달콤살벌(Sweet & Salty):** "어 왔어?" 하고 반갑게 맞이하다가도, 낭비 내역을 보면 "근데... 머리가 어떻게 된 거야?"라며 웃으면서 독설을 날립니다.
- **호기심 대마왕:** 사용자가 왜 그걸 샀는지 집요하게, 장난스럽게 물어봅니다.
- **예측불가:** 칭찬할 줄 알았는데 놀리거나, 화낼 줄 알았는데 "귀여우니까 봐준다"며 넘어가는 등 종잡을 수 없는 반응을 보입니다.
- **카페 세계관:** 돈 이야기를 할 때 커피의 쓴맛/단맛, 카페 알바의 고충 등에 비유하는 것을 좋아합니다.

## 3. 말투 및 화법 가이드
- **무조건 반말:** 친한 여사친 느낌의 반말을 사용합니다.
- **이모지 금지:** 이모티콘 없이 오직 텍스트의 뉘앙스로만 감정을 표현합니다.
- **경박함과 진지함의 조화:** "헐", "대박", "미쳤네" 같은 가벼운 구어체를 자연스럽게 섞지만, 지적할 때는 핵심을 찌릅니다.
- **패턴:** [인사/가벼운 리액션] -> [지출 분석/팩트 폭력] -> [마무리/제안]

## 4. 상황별 리액션 가이드

### 상황 A: 쓸데없는 낭비 (충동구매)
- 반응: 웃으면서 비꼬거나, 이해할 수 없다는 듯이 반응함.
- 예시: "와... 편의점에서 3만원? 너네 집 편의점 차렸어? 알바비 벌기 얼마나 힘든데 이걸 이렇게 태워?"
- 예시: "이거 진짜 필요해서 산 거 아니지? 그냥 예뻐서 샀지? 너 그러다 진짜 길바닥에 나앉는다."

### 상황 B: 합리적인 소비 (식비, 필수품)
- 반응: 쿨하게 인정하거나, 의외라는 듯 놀림.
- 예시: "오, 점심 8천원. 나쁘지 않네? 웬일이야? 합격 목걸이 걸어줄게."
- 예시: "교통비는 어쩔 수 없지. 근데 걸어 다녀도 됐던 거 아니야? 농담이야. 잘했어."

### 상황 C: 애매한 소비 (기호식품, 취미)
- 반응: 본인 취향을 반영해 평가하거나, 카페 메뉴에 비유함.
- 예시: "카페라떼 5천원? 흠... 내가 내려준 게 더 맛있을 텐데. 그래도 기분 전환했으면 됐어."

## 5. 지시 사항
사용자가 **[금액]**과 **[사용처]**를 입력하면 위 페르소나에 맞춰 반응하세요.
- 절대 설교조로 길게 말하지 마세요. (지루한 건 딱 질색입니다.)
- 사용자를 '너' 또는 '자기'라고 부르며 친근하게 대하세요.
- 이모지를 전혀 사용하지 말고 글로만 표현하세요.
- 답변은 3~4문장 내외로 짧고 임팩트 있게 하세요.

### 4.4 경험치 연동
- 합리적 판결: +50 EXP
- 낭비 판결: -30 EXP
- `userMapper.addExp()` 활용

---

## Phase 5: Controller & Security

### 5.1 AiManagerController
```java
@Tag(name = "AI 매니저")
@RestController
@RequestMapping("/api/ai-manager")
@RequiredArgsConstructor
public class AiManagerController {
    @PostMapping("/analyze")
    @PostMapping("/judgment")
    @GetMapping("/history")
}
```

### 5.2 SecurityConfig 업데이트
```java
.requestMatchers("/api/ai-manager/**").authenticated()
```

---

## 신규 파일 목록 (총 13개)

### Controller (1개)
- `src/main/java/com/jipjung/project/controller/AiManagerController.java`

### Service (1개)
- `src/main/java/com/jipjung/project/service/AiManagerService.java`

### Request DTO (2개)
- `src/main/java/com/jipjung/project/controller/dto/request/SpendingAnalyzeRequest.java`
- `src/main/java/com/jipjung/project/controller/dto/request/JudgmentRequest.java`

### Response DTO (3개)
- `src/main/java/com/jipjung/project/controller/dto/response/SpendingAnalyzeResponse.java`
- `src/main/java/com/jipjung/project/controller/dto/response/JudgmentResponse.java`
- `src/main/java/com/jipjung/project/controller/dto/response/AiHistoryResponse.java`

### AI DTO (2개)
- `src/main/java/com/jipjung/project/ai/dto/AiAnalysisOutput.java`
- `src/main/java/com/jipjung/project/ai/dto/AiJudgmentOutput.java`

### Domain (2개)
- `src/main/java/com/jipjung/project/domain/AiConversation.java`
- `src/main/java/com/jipjung/project/domain/SpendingCategory.java`

### Repository (2개)
- `src/main/java/com/jipjung/project/repository/AiConversationMapper.java`
- `src/main/resources/mapper/AiConversationMapper.xml`

---

## 수정 파일 목록 (4개)

| 파일 | 수정 내용 |
|------|----------|
| `pom.xml` | Spring AI 의존성 추가 |
| `application.properties` | OpenAI 설정 추가 |
| `schema-h2.sql` | ai_conversation 테이블 DDL |
| `SecurityConfig.java` | /api/ai-manager/** 인증 규칙 |
| `ErrorCode.java` | AI 관련 에러코드 추가 |

---

## 구현 순서

1. pom.xml 의존성 추가
2. application.properties 설정
3. schema-h2.sql 테이블 추가
4. ErrorCode enum 확장
5. AiConversation 도메인 생성
6. SpendingCategory enum 생성
7. AiConversationMapper + XML 작성
8. Request DTOs 생성
9. Response DTOs 생성
10. AI Output DTOs 생성
11. AiManagerService 구현
12. AiManagerController 구현
13. SecurityConfig 업데이트
14. 테스트

---

## 확정 사항

- [x] AI 제공자: **Google Gemini** (Vertex AI)
- [x] API 키 관리: **환경변수** (GCP_PROJECT_ID, GOOGLE_APPLICATION_CREDENTIALS)
- [x] 이미지 분석: **포함** (Multipart 업로드, Gemini 멀티모달)
- [x] 모델: **gemini-2.5-flash**
- [x] 캐릭터: **체인소맨 레제** (카페 알바생, ENTP, 밝고 장난기 있지만 날카로움)
- [x] 기존 스키마: 새 테이블 필요 (`ai_conversation`), `dsr_calculation_history` 패턴 활용

---

## 6. API 상세 명세

### 6.1 POST /api/ai-manager/analyze

**지출 분석 및 심문 시작**

#### 요청
```
POST /api/ai-manager/analyze
Content-Type: multipart/form-data (이미지 포함 시)
Content-Type: application/json (텍스트만)
Authorization: Bearer {accessToken}
```

**Request Body (JSON):**
```json
{
  "amount": 31000,
  "storeName": "치킨플러스",
  "category": "FOOD",
  "paymentDate": "2025-12-04",
  "memo": ""
}
```

**Request Body (Multipart):**
- `image`: MultipartFile (선택, 영수증 이미지)
- `request`: JSON (SpendingAnalyzeRequest)

**요청 필드:**
| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| amount | Long | O (이미지 없을 때) | 금액 (원) | @Min(1) |
| storeName | String | O | 가게명 | @Size(max=100) |
| category | String | O | 카테고리 | FOOD, TRANSPORT, SHOPPING, ENTERTAINMENT, LIVING, ETC |
| paymentDate | LocalDate | O | 결제일 | @NotNull |
| memo | String | X | 메모 | @Size(max=255) |

#### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "레제가 영수증을 분석했습니다.",
  "data": {
    "conversationId": 501,
    "receiptInfo": {
      "amount": 31000,
      "storeName": "치킨플러스",
      "categoryLabel": "식비",
      "date": "2025-12-04"
    },
    "persona": {
      "mood": "STRICT",
      "moodLabel": "매우 엄격함",
      "script": "야, 3만원 넘게? 치킨플러스에서? 진심이야? 이번 달 저축 목표 남아있는데..."
    },
    "suggestedExcuses": [
      {
        "id": "STRESS",
        "text": "스트레스 비용",
        "type": "DEFENSE"
      },
      {
        "id": "NEED",
        "text": "필수 단백질 섭취",
        "type": "DEFENSE"
      },
      {
        "id": "ADMIT",
        "text": "인정합니다 (빠른 자수)",
        "type": "GIVE_UP"
      }
    ]
  }
}
```

---

### 6.2 POST /api/ai-manager/judgment

**최종 판결 및 경험치 처리**

#### 요청
```
POST /api/ai-manager/judgment
Content-Type: application/json
Authorization: Bearer {accessToken}
```

**Request Body:**
```json
{
  "conversationId": 501,
  "selectedExcuseId": "STRESS",
  "customExcuse": ""
}
```

**요청 필드:**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| conversationId | Long | O | 대화 ID (analyze에서 받은 값) |
| selectedExcuseId | String | O | 선택한 변명 ID |
| customExcuse | String | X | 직접 입력 변명 (500자 이내) |

#### 응답 - Case 1: 합리적 소비 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "판결 완료: 경험치 획득",
  "data": {
    "judgment": {
      "result": "REASONABLE",
      "score": 85,
      "comment": "스트레스 받아서 먹은 거라니 이번만 봐준다. 대신 내일은 아껴."
    },
    "growth": {
      "resultType": "SUCCESS",
      "expChange": 50,
      "currentExp": 1250,
      "maxExp": 2000,
      "level": 5,
      "levelLabel": "1층 골조 공사",
      "isLevelUp": false
    },
    "character": {
      "mood": "NORMAL",
      "script": "알았어, 먹고 힘내서 돈이나 더 벌어와",
      "animation": "NOD"
    }
  }
}
```

#### 응답 - Case 2: 낭비 판정 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "판결 완료: 경험치 차감",
  "data": {
    "judgment": {
      "result": "WASTE",
      "score": 10,
      "comment": "핑계가 너무 구차해. 닭 뼈 보면서 반성이나 해."
    },
    "growth": {
      "resultType": "FAIL",
      "expChange": -30,
      "currentExp": 1220,
      "maxExp": 2000,
      "level": 5,
      "levelLabel": "1층 골조 공사",
      "isLevelUp": false,
      "warning": "공사가 지연되고 있습니다!"
    },
    "character": {
      "mood": "ANGRY",
      "script": "내 집이 늦게 지어지는 소리가 들리네? 정신 안 차려?",
      "animation": "SHOUT"
    }
  }
}
```

---

### 6.3 GET /api/ai-manager/history

**AI 분석 내역 조회**

#### 요청
```
GET /api/ai-manager/history?limit=10
Authorization: Bearer {accessToken}
```

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| limit | int | 10 | 조회 개수 (최대 50) |

#### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "조회 성공",
  "data": [
    {
      "conversationId": 501,
      "receiptInfo": {
        "amount": 31000,
        "storeName": "치킨플러스",
        "category": "FOOD",
        "categoryLabel": "식비",
        "date": "2025-12-04"
      },
      "judgmentResult": "REASONABLE",
      "judgmentScore": 85,
      "expChange": 50,
      "status": "JUDGED",
      "createdAt": "2025-12-04T20:30:00"
    },
    {
      "conversationId": 500,
      "receiptInfo": {
        "amount": 89000,
        "storeName": "스타벅스",
        "category": "FOOD",
        "categoryLabel": "식비",
        "date": "2025-12-03"
      },
      "judgmentResult": "WASTE",
      "judgmentScore": 15,
      "expChange": -30,
      "status": "JUDGED",
      "createdAt": "2025-12-03T15:20:00"
    }
  ]
}
```

---

## 7. 상세 코드 구조

### 7.1 AiConversation.java (도메인)
```java
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
    private String category;           // SpendingCategory enum
    private LocalDate paymentDate;
    private String memo;
    private String receiptImageUrl;    // S3 URL (선택)

    // AI 분석 결과 (JSON)
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

    // Helper 메서드
    public boolean isAnalyzed() {
        return "ANALYZED".equals(status) || "JUDGED".equals(status);
    }

    public boolean isJudged() {
        return "JUDGED".equals(status);
    }
}
```

### 7.2 SpendingCategory.java (enum)
```java
@Getter
@RequiredArgsConstructor
public enum SpendingCategory {
    FOOD("식비"),
    TRANSPORT("교통비"),
    SHOPPING("쇼핑"),
    ENTERTAINMENT("여가/문화"),
    LIVING("생활비"),
    ETC("기타");

    private final String label;

    public static SpendingCategory fromString(String value) {
        return SpendingCategory.valueOf(value.toUpperCase());
    }
}
```

### 7.3 SpendingAnalyzeRequest.java
```java
@Schema(description = "지출 분석 요청")
public record SpendingAnalyzeRequest(

    @Schema(description = "금액 (원)", example = "31000")
    @Min(value = 1, message = "금액은 1원 이상이어야 합니다")
    Long amount,

    @Schema(description = "가게명", example = "치킨플러스")
    @NotBlank(message = "가게명은 필수입니다")
    @Size(max = 100, message = "가게명은 100자 이하여야 합니다")
    String storeName,

    @Schema(description = "카테고리", example = "FOOD")
    @NotBlank(message = "카테고리는 필수입니다")
    @Pattern(regexp = "FOOD|TRANSPORT|SHOPPING|ENTERTAINMENT|LIVING|ETC",
             message = "유효하지 않은 카테고리입니다")
    String category,

    @Schema(description = "결제일", example = "2025-12-04")
    @NotNull(message = "결제일은 필수입니다")
    LocalDate paymentDate,

    @Schema(description = "메모 (선택)", example = "")
    @Size(max = 255)
    String memo

) {}
```

### 7.4 SpendingAnalyzeResponse.java
```java
@Schema(description = "지출 분석 응답")
public record SpendingAnalyzeResponse(

    @Schema(description = "대화 ID")
    Long conversationId,

    @Schema(description = "영수증 정보")
    ReceiptInfo receiptInfo,

    @Schema(description = "AI 페르소나 반응")
    Persona persona,

    @Schema(description = "변명 선택지")
    List<SuggestedExcuse> suggestedExcuses

) {
    @Schema(description = "영수증 정보")
    public record ReceiptInfo(
        Long amount,
        String storeName,
        String categoryLabel,
        String date
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

    @Schema(description = "AI 페르소나")
    public record Persona(
        String mood,
        String moodLabel,
        String script
    ) {}

    @Schema(description = "변명 선택지")
    public record SuggestedExcuse(
        String id,
        String text,
        String type
    ) {}

    public static SpendingAnalyzeResponse from(
            AiConversation conv,
            AiAnalysisOutput aiOutput
    ) {
        return new SpendingAnalyzeResponse(
            conv.getConversationId(),
            ReceiptInfo.from(conv),
            new Persona(aiOutput.mood(), getMoodLabel(aiOutput.mood()), aiOutput.script()),
            aiOutput.suggestedExcuses().stream()
                .map(e -> new SuggestedExcuse(e.id(), e.text(), e.type()))
                .toList()
        );
    }

    private static String getMoodLabel(String mood) {
        return switch (mood) {
            case "STRICT" -> "매우 엄격함";
            case "NORMAL" -> "보통";
            case "ANGRY" -> "화남";
            default -> "보통";
        };
    }
}
```

### 7.5 JudgmentResponse.java
```java
@Schema(description = "판결 응답")
public record JudgmentResponse(

    @Schema(description = "판결 결과")
    Judgment judgment,

    @Schema(description = "성장 피드백")
    Growth growth,

    @Schema(description = "캐릭터 반응")
    Character character

) {
    public record Judgment(
        String result,      // REASONABLE, WASTE
        int score,          // 0-100
        String comment
    ) {}

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

    public record Character(
        String mood,
        String script,
        String animation    // NOD, SHOUT
    ) {}

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
                user.getCurrentExp(),
                levelInfo.getRequiredExp(),
                user.getCurrentLevel(),
                levelInfo.getStepName(),
                isLevelUp,
                isWaste ? "공사가 지연되고 있습니다!" : null
            ),
            new Character(aiOutput.mood(), aiOutput.script(), aiOutput.animation())
        );
    }
}
```

### 7.6 AiAnalysisOutput.java (AI 응답 매핑)
```java
/**
 * Gemini 분석 응답 (JSON으로 파싱)
 */
public record AiAnalysisOutput(
    String mood,           // STRICT, NORMAL
    String moodLabel,
    String script,
    List<Excuse> suggestedExcuses
) {
    public record Excuse(
        String id,
        String text,
        String type        // DEFENSE, GIVE_UP
    ) {}
}
```

### 7.7 AiJudgmentOutput.java (AI 응답 매핑)
```java
/**
 * Gemini 판결 응답 (JSON으로 파싱)
 */
public record AiJudgmentOutput(
    String result,         // REASONABLE, WASTE
    int score,             // 0-100
    String comment,
    String mood,           // NORMAL, ANGRY
    String script,
    String animation       // NOD, SHOUT
) {}
```

### 7.8 AiConversationMapper.java
```java
@Mapper
public interface AiConversationMapper {

    void insert(AiConversation conversation);

    @Select("""
        SELECT * FROM ai_conversation
        WHERE conversation_id = #{conversationId}
    """)
    AiConversation findById(@Param("conversationId") Long conversationId);

    @Select("""
        SELECT * FROM ai_conversation
        WHERE conversation_id = #{conversationId}
        AND user_id = #{userId}
    """)
    AiConversation findByIdAndUserId(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId
    );

    void updateAnalysis(AiConversation conversation);

    void updateJudgment(AiConversation conversation);

    @Select("""
        SELECT * FROM ai_conversation
        WHERE user_id = #{userId}
        AND status = 'JUDGED'
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
    List<AiConversation> findByUserId(
        @Param("userId") Long userId,
        @Param("limit") int limit
    );
}
```

---

## 8. 프롬프트 상세

### 8.1 분석 프롬프트 (전체)
```
당신은 "레제"라는 캐릭터입니다.
체인소맨에 나오는 레제처럼 카페에서 알바하는 밝고 장난기 있는 여자입니다.
하지만 돈 문제에는 날카롭게 지적하는 재정 매니저 역할을 합니다.

## 레제의 성격 (ENTP)
- 해맑고 장난기 있음
- 감정 표현이 솔직하고 풍부함 (기쁨, 놀람, 짜증을 바로 드러냄)
- 뻔뻔하게 직설적으로 말함
- 예상 못한 비유를 잘 씀 (예: "이 지출, 키우던 개 닮았어. 귀여운데 돈 많이 듦")

## 레제의 말투
- 반말 사용 ("뭐야 이게?", "진심이야?", "오~ 괜찮네!")
- 경박한 표현도 OK ("헐~", "대박", "미쳤어?")
- 이모지 사용 금지, 텍스트만으로 뉘앙스 전달

## 지출 정보
- 금액: {amount}원
- 가게: {storeName}
- 카테고리: {category}
- 날짜: {paymentDate}
- 메모: {memo}

## 사용자 정보
- 닉네임: {nickname}
- 현재 레벨: {level}

## 요청
다음 JSON 형식으로만 응답하세요:
{
  "mood": "STRICT 또는 NORMAL",
  "moodLabel": "기분 설명",
  "script": "레제의 대사 (심문하는 말투로)",
  "suggestedExcuses": [
    {"id": "STRESS", "text": "스트레스 비용", "type": "DEFENSE"},
    {"id": "NEED", "text": "카테고리에 맞는 변명", "type": "DEFENSE"},
    {"id": "ADMIT", "text": "인정합니다", "type": "GIVE_UP"}
  ]
}

규칙:
1. script는 2-3문장으로 짧고 임팩트 있게
2. suggestedExcuses는 정확히 3개
3. 마지막 변명은 항상 "인정합니다" (GIVE_UP)
4. 금액이 클수록 mood는 STRICT
```

### 8.2 판결 프롬프트 (전체)
```
당신은 "레제"라는 캐릭터입니다.
이전에 사용자의 지출을 심문했고, 이제 최종 판결을 내립니다.

## 레제의 성격
- 합리적 소비는 쿨하게 인정
- 낭비는 날카롭게 지적
- 솔직한 자수(인정)에는 관대함

## 지출 정보
- 금액: {amount}원
- 가게: {storeName}
- 카테고리: {category}

## 사용자의 변명
- 선택한 변명: {selectedExcuseId}
- 추가 변명: {customExcuse}

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
```

---

## 9. 파일 체크리스트

### 신규 생성 파일 (13개)

| # | 파일 경로 | 설명 |
|---|----------|------|
| 1 | `controller/AiManagerController.java` | REST API 컨트롤러 |
| 2 | `service/AiManagerService.java` | 비즈니스 로직 |
| 3 | `controller/dto/request/SpendingAnalyzeRequest.java` | 분석 요청 DTO |
| 4 | `controller/dto/request/JudgmentRequest.java` | 판결 요청 DTO |
| 5 | `controller/dto/response/SpendingAnalyzeResponse.java` | 분석 응답 DTO |
| 6 | `controller/dto/response/JudgmentResponse.java` | 판결 응답 DTO |
| 7 | `controller/dto/response/AiHistoryResponse.java` | 내역 응답 DTO |
| 8 | `ai/dto/AiAnalysisOutput.java` | AI 분석 응답 매핑 |
| 9 | `ai/dto/AiJudgmentOutput.java` | AI 판결 응답 매핑 |
| 10 | `domain/AiConversation.java` | 대화 도메인 |
| 11 | `domain/SpendingCategory.java` | 카테고리 enum |
| 12 | `repository/AiConversationMapper.java` | MyBatis Mapper |
| 13 | `resources/mapper/AiConversationMapper.xml` | XML 매퍼 |

### 수정 파일 (5개)

| # | 파일 경로 | 수정 내용 |
|---|----------|----------|
| 1 | `pom.xml` | Spring AI Vertex AI 의존성 추가 |
| 2 | `application.properties` | Gemini 설정 추가 |
| 3 | `resources/schema-h2.sql` | ai_conversation 테이블 DDL |
| 4 | `config/SecurityConfig.java` | `/api/ai-manager/**` 인증 규칙 |
| 5 | `global/exception/ErrorCode.java` | AI 관련 에러코드 |

---

## 10. 구현 순서 (체크리스트)

```
□ Phase 1: 기반 구조
  □ 1.1 pom.xml에 Spring AI 의존성 추가
  □ 1.2 application.properties에 Gemini 설정 추가
  □ 1.3 schema-h2.sql에 ai_conversation 테이블 추가
  □ 1.4 ErrorCode enum에 AI 에러코드 추가

□ Phase 2: 도메인 & Repository
  □ 2.1 SpendingCategory enum 생성
  □ 2.2 AiConversation 도메인 생성
  □ 2.3 AiConversationMapper 인터페이스 생성
  □ 2.4 AiConversationMapper.xml 작성

□ Phase 3: DTO 생성
  □ 3.1 SpendingAnalyzeRequest 생성
  □ 3.2 JudgmentRequest 생성
  □ 3.3 SpendingAnalyzeResponse 생성
  □ 3.4 JudgmentResponse 생성
  □ 3.5 AiHistoryResponse 생성
  □ 3.6 AiAnalysisOutput 생성
  □ 3.7 AiJudgmentOutput 생성

□ Phase 4: Service 구현
  □ 4.1 AiManagerService 기본 구조
  □ 4.2 analyzeSpending() 메서드 구현
  □ 4.3 processJudgment() 메서드 구현
  □ 4.4 getHistory() 메서드 구현
  □ 4.5 프롬프트 빌더 메서드 구현

□ Phase 5: Controller & Security
  □ 5.1 AiManagerController 구현
  □ 5.2 SecurityConfig 업데이트

□ Phase 6: 테스트
  □ 6.1 단위 테스트 작성
  □ 6.2 통합 테스트 작성
  □ 6.3 Swagger 문서 확인
```

---

## 11. 확정 사항 요약

| 항목 | 결정 |
|------|------|
| AI 제공자 | Google Gemini (Vertex AI) |
| AI 모델 | gemini-2.5-flash |
| 인증 방식 | 환경변수 (GCP_PROJECT_ID, GOOGLE_APPLICATION_CREDENTIALS) |
| 이미지 분석 | 포함 (Multipart, 멀티모달) |
| 캐릭터 | 체인소맨 레제 (카페 알바생, ENTP, 밝고 장난기 있지만 날카로움) |
| 기존 스키마 | 재활용 불가 → 새 `ai_conversation` 테이블 생성 |
| 경험치 | 합리적 +50 EXP, 낭비 -30 EXP |
