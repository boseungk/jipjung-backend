# 📋 [Tech Spec] 집-중 (Zip-Jung) : DSR 엔진 기반 게이미피케이션 & UX 고도화

## 1. 개요 (Overview)

본 문서는 '집-중' 서비스의 핵심 가치인 **"현실적인 내 집 마련 솔루션"**을 구현하기 위한 API 및 로직 설계서이다.
사용자의 진입 장벽을 낮추기 위해 **Progressive Disclosure(점진적 정보 공개)** UX를 채택하며, 백엔드 코어에는 **2026년형 DSR/스트레스 금리 엔진**을 탑재한다.

---

## 2. 사용자 시나리오 (UX Flow)

### Phase 1: 가벼운 온보딩 (Lite Onboarding)

- **목표:** 30초 내 게임 진입. 복잡한 금융 정보 입력 배제.
- **입력:** 닉네임, 출생년도(나이), 현재 자산, **선호 지역(Region)**.
- **상태:** `Target Not Set` (목표 매물 미정).

### Phase 2: 탐색 및 목표 설정 (Shopping & Commitment)

- **대시보드:** 선호 지역의 '평균 시세'를 가상의 적으로 설정하여 게임 루프 가동.
- **액션:** [목표 정하기] 메뉴 진입 → 선호 지역 지도 탐색 → 특정 아파트 선택.
- **결과:** 목표 매물 확정(`Target Set`), 구체적인 Gap(목표 금액) 계산 시작.

### Phase 3: 정교한 DSR 시뮬레이션 (Deep Dive)

- **진입:** 대시보드 내 [💰 내 진짜 한도 조회/늘리기] 버튼.
- **입력:** 연봉, 기존 부채, 전세 자금 정보, 금리 유형(변동/주기형 등).
- **엔진:** `DsrCalculator2026` 가동 → 2026년 정책(스트레스 DSR 3단계, 청년 소득 등) 반영.
- **보상:** 더 정확한(혹은 더 늘어난) 대출 한도를 통해 게임 난이도(Gap) 재조정.

---

## 3. 데이터 모델 (Schema)

### 3-1. UserFinance (사용자 재무 상태)

JSON

`{
"nickname": "건물주",
"birthYear": 1994,
"currentAssets": 50000000,
"preferredRegionCode": "11440", // 법정동 코드 (예: 서울 마포구)
"targetApartmentId": null, // 초기엔 null

// --- 아래는 Phase 3(시뮬레이터) 입력 전까지 Nullable ---
"annualIncome": null,
"existingAnnualDebt": 0,
"jeonseLoanBalance": 0,
"jeonseLoanRate": 0.0,
"dsrMode": "LITE" // LITE(가상 데이터) vs PRO(실제 데이터)
}`

### 3-2. DsrCalculationRequest (시뮬레이터 입력 - Java DsrInput 매핑)

JSON

`{
  "annualIncome": 60000000,
  "age": 32, // birthYear로 서버에서 계산
  "region": "SEOUL_METRO",
  "existingAnnualDebtService": 3000000,
  "jeonseLoanBalance": 200000000,
  "jeonseLoanRate": 4.0,
  "jeonseIncludedInDsr": true, // 1주택자 수도권 전세 여부
  "targetLoanType": "PERIODIC", // VARIABLE, MIXED, PERIODIC, FIXED
  "targetLoanRate": 4.0,
  "maturityYears": 40,
  "lenderType": "BANK"
}`

---

## 4. API 명세 (API Specification)

### 4-1. 인증 & 온보딩

**`PUT /onboarding`**

- **설명:** 회원가입 직후 최소 정보 수집.
- **Request:**JSON

  `{
    "nickname": "레제바라기",
    "birthYear": 1995,
    "currentAssets": 30000000,
    "preferredRegionCode": "11440" // 마포구
  }`

- **Logic:**
    - `dsrMode`를 `LITE`로 설정.
    - 내부적으로 대한민국 중위 소득, 표준 DSR 값을 임시 할당하여 게임 엔진 초기화.

### 4-2. 대시보드 (메인)

**`GET /dashboard`**

- **설명:** 현재 목표 상태에 따른 동적 응답.
- **Response:**JSON

  `{
    "code": 200,
    "data": {
      "status": {
        "hasTarget": false, // 목표 매물 설정 여부
        "regionName": "마포구",
        "regionAvgPrice": 950000000 // hasTarget=false일 때 보여줄 임시 목표
      },
      "showroom": {
        "step": 0,
        "imageUrl": "https://.../empty_land.png", // 공터 이미지
        "description": "아직 지을 집을 정하지 않았어요."
      },
      "gapAnalysis": {
        "targetAmount": 950000000,
        "currentAssets": 30000000,
        "virtualLoanLimit": 300000000, // LITE 모드 추정치
        "requiredSavings": 620000000 // Game Goal
      }
    }
  }`


### 4-3. 목표 설정 (탐색)

**`GET /apartments?regionCode={code}`**

- **설명:** 온보딩 때 선택한 지역의 매물 리스트 반환.
- **Tip:** `regionCode` 파라미터가 없으면 User Profile의 `preferredRegionCode` 사용.

**`POST /goals/target`**

- **설명:** 목표 매물 확정 (Commitment).
- **Request:** `{ "apartmentId": 10523 }`
- **Response:** 기초 공사 시작 애니메이션 트리거 데이터 반환.

### 4-4. DSR 시뮬레이터 (핵심 엔진)

**`POST /simulation/dsr`**

- **설명:** 2026년 정책 기반 상세 한도 계산 및 사용자 프로필 업데이트.
- **Request:** `3-2. DsrCalculationRequest` 구조 참조.
- **Response:**JSON

  `{
    "code": 200,
    "data": {
      "userGrade": "SAFE", // DSR 등급
      "maxLoanAmount": 420000000, // 계산된 최대 한도
      "appliedPolicy": {
        "stressDsrRate": 1.2, // 적용된 가산 금리 (예: 3.0 * 0.4)
        "youthIncomeMultiplier": 1.131 // 적용된 장래소득 인정 비율
      },
      "simulationTip": "변동금리 대신 주기형을 선택해서 한도가 5,000만원 늘어났어요!",
      "gameUpdate": {
        "reducedGap": 50000000, // 줄어든 목표 금액
        "expGained": 500 // 보상 경험치
      }
    }
  }`


---

## 5. 비즈니스 로직 구현 가이드 (Backend Strategy)

### 5-1. DSR 정책 주입 (`DsrPolicy`)

제공된 Java 코드를 기반으로 **2025.12 기준 정책(bankDefault2025H2)**을 기본값으로 사용한다.

- **수도권(서울/경기):** 스트레스 금리 하한 **3.0%** 적용.
- **비수도권:** 스트레스 금리 **0.75%** (3단계 유예) 적용.
- **전세대출:** `jeonseIncludedInDsr=true`일 경우, 전세 이자를 DSR 부채(분자)에 가산.

### 5-2. 청년 장래소득 (`DsrCalculator`)

- 사용자 `birthYear`를 기준으로 만 나이 계산.
- 만 20~34세 구간에 해당할 경우 `DsrPolicy`의 Multiplier (151.6% ~ 113.1%) 자동 적용.

### 5-3. 시뮬레이션 결과의 게임 데이터 반영

- DSR 시뮬레이션 결과(`maxLoanAmount`)가 갱신되면, 즉시 사용자의 **`GameGoal.requiredSavings` (저축 목표액)**을 재계산한다.
    - 공식: `TargetPrice - (CurrentAssets + MaxLoanAmount)`
- 한도가 늘어나면 → 저축 목표가 줄어듦 → **"집 짓기 난이도 하락 & 공사 속도 가속"** 효과 연출.