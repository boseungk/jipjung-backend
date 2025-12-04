# 집-중 (Jip-joong) REST API 명세서

## 개요

**프로젝트**: 감성 저축 게이미피케이션 앱
**API 버전**: v1.0
**Base URL**: `http://localhost:8080/api`
**인증 방식**: JWT Bearer Token
**총 API 개수**: 24개 (구현 완료: 7개 | 개발 계획: 17개)
**현재 구현 단계**: Phase 1 - MVP 기초 구현 (29% 완료)

---

## API 요약

### ✅ 구현 완료 (7개)

| 카테고리 | 개수 | 엔드포인트 | 설명 |
|---------|------|----------|------|
| **인증** | 2 | POST /api/auth/signup, POST /api/auth/login | 회원가입, 로그인 |
| **아파트** | 5 | GET /api/apartments, GET /api/apartments/{aptSeq}, POST /api/apartments/favorites, GET /api/apartments/favorites, DELETE /api/apartments/favorites/{id} | 아파트 조회 및 관심 관리 |



## 인증 설정

모든 API 요청은 다음 헤더를 포함합니다:

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

## 응답 형식

### 성공 응답 (200, 201)
```json
{
  "code": 200,
  "status": "OK",
  "message": "조회 성공",
  "data": { /* 실제 데이터 */ }
}
```

### 에러 응답 (4xx, 5xx)
```json
{
  "code": 400,
  "status": "BAD_REQUEST",
  "message": "입력값이 유효하지 않습니다",
  "data": null
}
```

### 공통 응답 필드
- `code` (number): HTTP 상태 코드 (200, 201, 400, 401, 404, 409, 500 등)
- `status` (string): 상태 문자열 (OK, CREATED, BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, CONFLICT, INTERNAL_SERVER_ERROR 등)
- `message` (string): 사용자 친화적 메시지
- `data` (object|array|null): 실제 응답 데이터 (에러 시 null)

---

# 1. 인증 API (2개) ✅

## 1-1. POST /api/auth/signup
**회원가입**

### 요청
```json
{
  "email": "user@example.com",
  "password": "Test1234!@",
  "nickname": "홍길동"
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 검증 | 예시 |
|------|------|------|------|------|
| email | string | O | @Email, @NotBlank | user@example.com |
| password | string | O | 8자 이상, 영문+숫자+특수문자 | Test1234!@ |
| nickname | string | O | 2-20자 | 홍길동 |

### 응답 (201 Created)
```json
{
  "code": 201,
  "status": "CREATED",
  "message": "회원가입 성공",
  "data": {
    "email": "user@example.com",
    "nickname": "홍길동"
  }
}
```

### 에러
- `400`: 유효성 검증 실패 (필드별 오류 메시지)
- `409`: 중복 이메일 (DUPLICATE_EMAIL)

---

## 1-2. POST /api/auth/login
**로그인**

### 요청
```json
{
  "email": "user@example.com",
  "password": "Test1234!@"
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 검증 | 예시 |
|------|------|------|------|------|
| email | string | O | @Email, @NotBlank | user@example.com |
| password | string | O | @NotBlank | Test1234!@ |

### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "로그인 성공",
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "dGhpcy4uLi",
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "건축왕",
      "onboardingCompleted": true // 프론트에서 대시보드로 갈지, 온보딩으로 갈지 판단용
    }
  }
}
```

### 주의사항
- JWT 토큰은 응답 헤더의 `Authorization` 헤더 또는 필터에서 처리됨
- 응답 body에는 최소 정보만 포함

### 에러
- `400`: 유효성 검증 실패
- `401`: 이메일 또는 비밀번호 오류

---

## 온보딩 & 프로필 API (2개)

## 2-1. POST /api/auth/onboarding
**온보딩 정보 저장**

사용 화면: `OnboardingView.vue`

### 요청
```json
{
  "birthYear": 1995,
  "annualIncome": 50000000,
  "existingLoanMonthly": 500000,
  "preferredAreas": ["강남구", "서초구", "송파구"]
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 예시 |
|------|------|------|------|------|
| birthYear | number | O | 출생년도 | 1995 |
| annualIncome | number | O | 연소득 (만원 단위) | 50000000 |
| existingLoanMonthly | number | O | 월 대출 상환액 (만원) | 500000 |
| preferredAreas | array | O | 선호 지역 배열 | ["강남구", "서초구"] |

### 응답 (200)
```json
{
  "code": 200,
  "status": "OK",
  "message": "온보딩 정보 저장 완료",
  "data": {
    // 1. 업데이트된 유저 핵심 정보
    "user": {
      "id": 1,
      "nickname": "건축왕",
      "onboardingCompleted": true // 이제 true가 되었음을 확인 사살
    },

    // 2. 입력한 금융 정보에 대한 '즉각적인 평가' (연출용)
    "dsrResult": {
      "dsrRatio": 12.0,
      "grade": "SAFE", // SAFE, CAUTION, DANGER
      "maxLoanAmount": 400000000
    },

    // 3. 레제의 첫 반응 (대시보드 가기 전, 모달이나 화면 전환용)
    "initialReaction": {
      "mood": "HAPPY",
      "script": "오~ 꽤 건실하네? 기초공사부터 튼튼히 시작해보자!"
    }
  }
}
```

---

## 2-2. Post /api/users/profile
**프로필 수정**

사용 화면: `ProfileSettingsView.vue`

### 요청
```json
{
  "nickname": "건축왕2세", // name 대신 nickname
  "annualIncome": 60000000,
  "existingLoanMonthly": 400000
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | O | 사용자 이름 |
| birthYear | number | O | 출생년도 |
| annualIncome | number | O | 연소득 (만원 단위) |
| existingLoanMonthly | number | O | 월 대출 상환액 (만원) |

### 응답 (200)
```json
{
  "code": 200,
  "status": "OK",
  "message": "프로필이 수정되었습니다.",
  "data": {
    // 1. 수정된 당사자 정보 (확인용)
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "건축왕2세",
      "annualIncome": 60000000,
      "existingLoanMonthly": 400000,
      "updatedAt": "2025-12-02T15:30:00Z"
    }
  }
}
```

---

# 🔄 향후 개발 예정 API (17개)

## 3-1. GET /api/users/dashboard
**대시보드 통합 데이터 조회**

사용 화면: `DashboardView.vue`

### 요청
```
GET /api/users/dashboard
Authorization: Bearer {accessToken}
```

### 응답 (200)
```json
{
  "code": 200,
  "status": "OK",
  "message": "대시보드 데이터 조회 성공",
  "data": {
    // [Section 1] 좌측 상단: 프로필 카드
    "profile": {
      "nickname": "강보승",
      "title": "집나무 숲지기", // 레벨 타이틀
      "statusMessage": "목표를 향해 천천히, 꾸준히 가고 있어요", // 유저 상태 메시지 또는 레제의 한마디
      "level": 5,
      "levelProgress": {
        "currentExp": 150,
        "targetExp": 170,
        "percent": 88.2,
        "remainingExp": 20
      }
    },

    // [Section 2] 우측 상단: 목표 달성률 (도넛 차트)
    "goal": {
      "targetPropertyName": "래미안 아파트",
      "totalAmount": 150000000,   // 1.5억 (단위: 원)
      "savedAmount": 150000000,   // 현재 모은 금액
      "remainingAmount": 0,       // 남은 금액
      "achievementRate": 100.0,   // 달성률
      "isCompleted": true         // 100% 달성 여부 (저축하기 버튼 활성화 로직 등)
    },

    // [Section 3] 좌측 중단: 연속 저축 (스트릭)
    "streak": {
      "currentStreak": 7,
      "maxStreak": 15,
      "isTodayParticipated": false, // 오늘 불꽃을 켰는지 여부 (false면 클릭 유도)
      "rewardAvailable": true,      // "+50 XP 받기" 가능 여부
      // 월~일 요일별 달성 여부 (UI의 불꽃 아이콘 활성화용)
      "weeklyStatus": [
        { "day": "MON", "achieved": true },
        { "day": "TUE", "achieved": true },
        { "day": "WED", "achieved": true },
        { "day": "THU", "achieved": true },
        { "day": "FRI", "achieved": false }, // 미래거나 미달성
        { "day": "SAT", "achieved": false },
        { "day": "SUN", "achieved": false }
      ]
    },

    // [Section 4] 우측 중단: DSR (게이지 차트)
    "dsr": {
      "dsrPercent": 24.0,
      "gradeLabel": "매우 안전", // UI 뱃지 텍스트
      "gradeColor": "GREEN",    // UI 색상 코드 (GREEN, YELLOW, RED)
      "financialInfo": {
        "monthlyIncome": 4160000,
        "existingLoanRepayment": 1000000, // 기존 상환
        "availableCapacity": 1660000      // 여력 (추가 대출 가능 상환액)
      }
    },

    // [Section 5] 하단: 자산 성장 (라인 차트)
    "assets": {
      "totalAsset": 42500000,
      "growthAmount": 37500000, // 지난달 대비 또는 시작일 대비 증가액
      "growthRate": 750.0,      // 증가율
      // 차트용 데이터 배열 (최근 7일 or 30일)
      "chartData": [
        { "date": "10-01", "amount": 10000000 },
        { "date": "10-02", "amount": 10050000 },
        // ... 중간 생략 ...
        { "date": "10-13", "amount": 42500000 }
      ]
    },

    // [Section 6] 라이프스타일 쇼룸 (이미지 2번 관련)
    // 현재 짓고 있는 집의 시각적 상태
    "showroom": {
      "currentStep": 2,
      "totalSteps": 7,
      "stepTitle": "기둥 올리기",
      "stepDescription": "튼튼한 골조가 올라가요.",
      "imageUrl": "https://cdn.../house_step_02_isometric.png", // 현재 단계 이미지
    }

  }
}
```

---

# 2. 아파트 API (5개) ✅

## 2-1. GET /api/apartments
**아파트 목록 조회 (지도 핀 & 검색 리스트)**

- 변경점: 개별 거래(dealNo) 단위가 아니라 아파트 단지(aptSeq) 단위로 응답합니다.

- 목적: 지도에 핀을 찍거나 검색 리스트를 보여줄 때, 같은 아파트가 여러 번 나오지 않게 합니다.

- 데이터: 가격은 가장 최근 거래된 금액(recentPrice)을 대표값으로 노출합니다.

### 요청
```
GET /api/apartments?keyword=현대&minPrice=30000&maxPrice=100000&swLat=37.5&swLng=126.9&neLat=37.6&neLng=127.0
```

### 쿼리 파라미터
| **파라미터** | **설명** | **비고** |
| --- | --- | --- |
| `keyword` | 검색어 (아파트명/지역명) |  |
| `minPrice` | 최소 가격 (만원) |  |
| `maxPrice` | 최대 가격 (만원) |  |
| `swLat`, `swLng` | 지도 남서쪽 좌표 | **[Map]** 현재 보고 있는 지도 영역만 조회 |
| `neLat`, `neLng` | 지도 북동쪽 좌표 | **[Map]** |

### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "조회 성공",
  "data": {
    "apartments": [
      {
        "aptSeq": "11410-61",
        "aptNm": "금천현대",
        "umdNm": "홍제동",
        "roadNm": "연희로",
        "buildYear": 2015,
        "latitude": 37.5689043200000,
        "longitude": 126.9341234000000,
        "recentPrice": 450000,       // [변경] 가장 최근 거래 금액 (대표 가격)
        "recentDealDate": "2024-11-15", // [추가] 그 가격이 언제 거래된 건지
        "area": 84.5                  // [변경] 대표 면적 (또는 국민평형 기준)
      },
      {
        "aptSeq": "11410-62",
        "aptNm": "홍제삼성",
        "umdNm": "홍제동",
        "roadNm": "통일로",
        "buildYear": 2010,
        "latitude": 37.5701043200000,
        "longitude": 126.9351234000000,
        "recentPrice": 520000,
        "recentDealDate": "2024-12-01",
        "area": 84.5
      }
    ],
    "totalCount": 15,
    "page": 0,
    "size": 10,
    "totalPages": 2
  }
}
```

---

## 2-2. GET /api/apartments/{aptSeq}
**아파트 상세 조회**

### 요청
```
GET /api/apartments/11410-61
```

### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "조회 성공",
  "data": {
    // 1. 아파트 기본 정보
    "aptInfo": {
      "aptSeq": "11410-61",
      "aptNm": "금천현대",
      "umdNm": "홍제동",
      "jibun": "서대문구 홍제동 123-45",
      "roadNm": "연희로",
      "roadNmBonbun": "10",
      "roadNmBubun": "5",
      "buildYear": 2015,
      "latitude": 37.5689043200000,
      "longitude": 126.9341234000000
    },

    // 2. 거래 내역 (그래프용 데이터) - 최신순 정렬 권장
    "deals": [
      {
        "dealNo": 12345,
        "dealDate": "2024-11-15",
        "dealAmount": 450000,
        "floor": "5",
        "aptDong": "A",
        "exclusiveArea": 84.5
      },
      {
        "dealNo": 12346,
        "dealDate": "2024-10-20",
        "dealAmount": 430000,
        "floor": "3",
        "aptDong": "A",
        "exclusiveArea": 84.5
      },
      {
        "dealNo": 12340,
        "dealDate": "2024-09-05",
        "dealAmount": 420000,
        "floor": "7",
        "aptDong": "B",
        "exclusiveArea": 84.5
      }
    ]
  }
}
```

### 에러
- `404`: 아파트를 찾을 수 없음

---

## 2-3. POST /api/apartments/favorites
**관심 아파트 등록**

### 요청
```json
{
  "aptSeq": "11410-61"
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 설명 | 예시 |
|------|------|------|------|------|
| aptSeq | string | O | 아파트 코드 | 11410-61 |

### 응답 (201 Created)
```json
{
  "code": 201,
  "status": "CREATED",
  "message": "관심 아파트로 등록되었습니다",
  "data": {
    "id": 42,             // 생성된 찜 고유 ID (DELETE 할 때 사용)
    "aptSeq": "11410-61",
    "aptNm": "금천현대",
    "umdNm": "홍제동",
    "recentPrice": 450000, // [New] 현재 시세 바로 확인
    "area": 84.5,
    "createdAt": "2024-11-15T14:30:25"
  }
}
```

### 인증
- JWT Bearer Token 필수

### 에러
- `400`: 이미 등록된 관심 아파트 또는 유효하지 않은 요청
- `401`: 인증 실패

---

## 2-4. GET /api/apartments/favorites
**관심 아파트 목록 조회**

### 요청
```
GET /api/apartments/favorites
Authorization: Bearer {accessToken}
```

### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "조회 성공",
  "data": [
    {
      "id": 42,
      "aptSeq": "11410-61",
      "aptNm": "금천현대",
      "umdNm": "홍제동",
      "roadNm": "연희로",
      "buildYear": 2015,
      "recentPrice": 450000,
      "latitude": 37.5689043200000,
      "longitude": 126.9341234000000,
      "createdAt": "2024-11-15T14:30:25"
    },
    {
      "id": 43,
      "aptSeq": "11410-62",
      "aptNm": "독산삼성",
      "umdNm": "독산동",
      "roadNm": "금천로",
      "buildYear": 2010,
      "latitude": 37.4689043200000,
      "longitude": 126.8341234000000,
      "createdAt": "2024-11-14T10:20:15"
    }
  ]
}
```

### 인증
- JWT Bearer Token 필수

---

## 2-5. DELETE /api/apartments/favorites/{id}
**관심 아파트 삭제**

### 요청
```
DELETE /api/apartments/favorites/42
Authorization: Bearer {accessToken}
```

### 경로 파라미터
| 파라미터 | 타입 | 필수 | 설명 | 예시 |
|---------|------|------|------|------|
| id | number | O | 관심 아파트 ID | 42 |

### 응답 (200 OK)
```json
{
  "code": 200,
  "status": "OK",
  "message": "관심 아파트 삭제 완료",
  "data": null
}
```

### 인증
- JWT Bearer Token 필수

### 권한
- 본인의 관심 아파트만 삭제 가능

### 에러
- `400`: 본인의 관심 아파트가 아님
- `401`: 인증 실패
- `404`: 관심 아파트를 찾을 수 없음

---

# 5. AI 매니저 API (Game Loop)  (3개)

### 5-1. POST /api/ai-manager/analyze

**지출 분석 및 심문 시작**

- 영수증(이미지) 또는 내역(텍스트)을 보내면 AI가 분석하여 심문을 시작합니다.

### 요청

- `MultipartFile` (image) 또는 JSON Body 사용

```json

{
  "amount": 31000,
  "storeName": "치킨플러스",
  "category": "FOOD",
  "paymentDate": "2025-12-04",
  "memo": "" // (선택) 유저가 미리 적은 메모
}
```

### 응답 (200 OK)

```json
{
  "code": 200,
  "status": "OK",
  "message": "레제가 영수증을 분석했습니다.",
  "data": {
    "tempReceiptId": 501, // 2단계(판결) 요청 시 필요

    // 1. 영수증 기본 정보 (화면 좌측 표시)
    "receiptInfo": {
      "amount": 31000,
      "storeName": "치킨플러스",
      "categoryLabel": "식비",
      "date": "2025-12-04"
    },

    // 2. AI 페르소나 (심문 모드)
    "persona": {
      "mood": "STRICT", // 프론트: 팔짱 끼고 노려보는 이미지
      "moodLabel": "매우 엄격함",
      "script": "아, 지금 일일 돈 거 뭐야? 31,000원? 이번 달 돈표 자숙액까지 12만 원 남았는데, 진짜 괜찮겠어?"
    },

    // 3. 변명 선택지 (Chips) - 카테고리에 맞춰 동적 생성
    "suggestedExcuses": [
      {
        "id": "STRESS",
        "text": "🤯 스트레스 비용",
        "type": "DEFENSE" // 방어형 변명
      },
      {
        "id": "NEED",
        "text": "🍖 필수 단백질 섭취",
        "type": "DEFENSE"
      },
      {
        "id": "ADMIT",
        "text": "🏳️ 인정합니다 (빠른 자수)",
        "type": "GIVE_UP" // 포기형 (페널티 감소 가능성)
      }
    ]
  }
}
```

### 3-2. POST /api/ai-manager/judgment

**최종 판결 및 보상/벌칙 부여**

- 유저의 변명을 듣고 최종 판결(합리/낭비)을 내리며 아이템을 지급합니다.

### 요청

```json
{
  "tempReceiptId": 501,
  "selectedExcuseId": "STRESS", // 유저가 선택한 칩 ID
  "customExcuse": "" // (선택) 직접 입력한 텍스트
}
```

### 응답 Case 1: 성공 (합리적 소비 인정) -> 경험치 획득

```json
{
  "code": 200,
  "status": "OK",
  "message": "판결 완료: 경험치 획득",
  "data": {
    // 1. 판결 결과
    "judgment": {
      "result": "REASONABLE",
      "score": 85, // 합리성 점수
      "comment": "스트레스 받아서 먹은 거라니 이번만 봐준다. 대신 내일은 아껴."
    },

    // 2. 성장(집 짓기) 피드백 - 핵심 변경 부분
    "growth": {
      "resultType": "SUCCESS", // SUCCESS(상승), FAIL(하락), KEEP(유지)
      "expChange": 50,         // "+50" (화면에 크게 연출)
      "currentExp": 1250,      // 현재 누적 경험치
      "maxExp": 2000,          // 다음 레벨까지 필요한 경험치 (게이지 바 용도)
      "level": 5,              // 현재 레벨
      "levelLabel": "1층 골조 공사", // 레벨 이름
      "isLevelUp": false       // true면 레벨업 축하 팝업/이펙트 실행
    },

    // 3. 레제 반응
    "character": {
      "mood": "NORMAL", // 화가 풀림
      "script": "알았어, 먹고 힘내서 돈이나 더 벌어와.",
      "animation": "NOD" // 고개 끄덕임
    }
  }
}
```

### 응답 Case 2: 실패 (낭비 판정) -> 경험치 차감/패널티

```json
{
  "code": 200,
  "status": "OK",
  "message": "판결 완료: 경험치 차감",
  "data": {
    // 1. 판결 결과
    "judgment": {
      "result": "WASTE",
      "score": 10,
      "comment": "핑계가 너무 구차해. 닭 뼈 보면서 반성이나 해."
    },

    // 2. 성장(집 짓기) 피드백
    "growth": {
      "resultType": "FAIL",
      "expChange": -30,        // "-30" (화면 흔들림, 빨간색 연출)
      "currentExp": 1220,      // 경험치 깎임
      "maxExp": 2000,
      "level": 5,
      "levelLabel": "1층 골조 공사",
      "isLevelUp": false,
      "warning": "공사가 지연되고 있습니다!" // 추가 경고 메시지
    },

    // 3. 레제 반응
    "character": {
      "mood": "ANGRY", // 극대노
      "script": "내 집이 늦게 지어지는 소리가 들리네? 정신 안 차려?",
      "animation": "SHOUT" // 소리침/화냄
    }
  }
}
```
---

---

# 6. 드림홈 API (2개)

## 6-1. PUT /api/dream-home

**드림홈 설정 및 변경**

- **진입점:** 아파트 상세 조회(`GET /apartments/{id}`) 화면 하단 '목표 설정' 버튼.
- **로직:** 유저는 **`aptSeq` (매물 ID)**와 본인의 **목표(금액/날짜)**만 보냅니다. 아파트의 이름, 위치, 시세 정보는 백엔드가 DB에서 직접 조회하여 저장합니다. (데이터 위변조 방지)

### 요청
```json
{
  "aptSeq": "11410-61",      // 필수: 아파트 고유 ID
  "targetAmount": 300000000, // 필수: 내가 모을 목표 원금 (단위: 원)
  "targetDate": "2028-12-31",// 필수: 목표 날짜
  "monthlyGoal": 2500000     // 선택: 월 목표 저축액 (프론트 계산값 저장용)
}
```

### 응답 (200)
```json
{
  "code": 200,
  "status": "OK",
  "message": "드림홈 설정 완료",
  "data": {
    "dreamHome": {
      "dreamHomeId": 10,
      // 백엔드가 DB에서 조회해서 채워주는 정보
      "aptSeq": "11410-61",
      "propertyName": "금천현대",
      "location": "서울 서대문구 홍제동",
      "price": 850000000,   // 최신 실거래가

      // 유저 설정 정보
      "targetAmount": 300000000,
      "monthlyGoal": 2500000,
      "dDay": 1095,          // 남은 일수 (백엔드 계산)
      "achievementRate": 0.0 // 초기 달성률
    }
  }
}
```

---

## 6-2. POST /api/dream-home/savings

**저축 기록 (진행률 업데이트 + 경험치 획득)**

- **기능:** 목표 달성을 위해 저축을 기록합니다.
- **보상:** 저축은 성실한 행동이므로 **경험치(성장)**를 지급합니다. (AI 매니저 API와 응답 구조 통일)
### 요청
```json
{
  "amount": 1000000,      // 저축할 금액
  "saveType": "DEPOSIT",  // DEPOSIT(입금), WITHDRAW(출금/비상금사용)
  "memo": "12월 월급 저축"
}
```


### 응답 (200)
```json
{
  "code": 200,
  "status": "OK",
  "message": "저축 완료",
  "data": {
    // 1. 드림홈 현황 갱신
    "dreamHomeStatus": {
      "currentSavedAmount": 51000000, // 누적 저축액
      "targetAmount": 300000000,
      "achievementRate": 17.0         // (5100만 / 3억) * 100
    },

    // 2. 성장(집 짓기) 피드백 (AI 매니저와 동일 구조)
    "growth": {
      "resultType": "SUCCESS",
      "expChange": 100,        // 저축은 큰 점수 (+100)
      "currentExp": 1350,
      "maxExp": 2000,
      "level": 5,
      "isLevelUp": false,      // 레벨업 시 true
      "levelLabel": "1층 골조 공사"
    }
}
```

---
### **9-1. GET /api/collections**

**내 컬렉션 목록 조회 (하단 썸네일 리스트)**

- **목적:** 사용자가 완공한 집들(트로피)을 리스트로 보여줍니다.
- **특징:** 현재 '메인'으로 설정된 컬렉션이 무엇인지(`isMain`) 표시합니다.


**요청** : `GET /api/collections
Authorization: Bearer {accessToken}`

**응답** (200 OK)

```json
{
  "code": 200,
  "status": "OK",
  "message": "컬렉션 조회 성공",
  "data": {
    "collections": [
      {
        "collectionId": 101,
        "houseName": "서울 강남 오피스텔",
        "completedDate": "2024-11-20",
        "thumbnailUrl": "https://cdn.../thumb_gangnam_offi.png",
        "isMain": true // 현재 상단에 크게 떠 있는 집
      },
      {
        "collectionId": 102,
        "houseName": "부산 해운대 아파트",
        "completedDate": "2024-08-15",
        "thumbnailUrl": "https://cdn.../thumb_busan_apt.png",
        "isMain": false
      },
      {
        "collectionId": 103,
        "houseName": "제주 애월 타운하우스",
        "completedDate": "2024-05-01",
        "thumbnailUrl": "https://cdn.../thumb_jeju_town.png",
        "isMain": false
      }
    ]
  }
}

```

---

### **9-2. GET /api/collections/{collectionId}**

**컬렉션 상세 조회 (상단 메인 뷰)**

- **목적:** 하단 썸네일을 클릭했을 때, 상단에 **큰 이미지**와 **캐릭터(레제)**를 배치하기 위한 상세 정보입니다.
- **시각화:** 이미지 3번처럼 **집 안에 캐릭터가 서 있는 연출**을 위해 캐릭터 좌표나 상태 정보가 필요할 수 있습니다.

**요청**
`GET /api/collections/101`

**응답 (200 OK)**
```json
{
  "code": 200,
  "status": "OK",
  "message": "컬렉션 상세 조회 성공",
  "data": {
    "collectionId": 101,
    "houseName": "서울 강남 오피스텔",
    "description": "첫 번째로 완공한 나만의 오피스텔입니다.",
    "fullImageUrl": "https://cdn.../full_gangnam_offi.png", // 고화질 배경
    
    // [중요] 화면 속 레제(캐릭터) 연출 정보
    "character": {
      "isVisible": true,
      "outfit": "MAID_OUTFIT", // 컬렉션별로 다른 옷을 입을 수도 있음
      "action": "CLEANING",    // IDLE, CLEANING, WELCOMING 등 동작
      "position": { "x": 50, "y": 60 }, // 화면 내 배치 좌표 (퍼센트)
      "script": "주인님, 이 집은 정말 채광이 좋네요!" // 클릭 시 대사
    },

    // 획득한 뱃지나 성과 (선택 사항)
    "stats": {
      "totalSaved": 150000000,
      "durationDays": 365
    }
  }
}
```

---

### 구현 완료
1. **인증 API (2/5 완료)** - 회원가입, 로그인
2. **아파트 API (5/6 완료)** - 목록 조회, 상세 조회, 관심 관리 (3개)


