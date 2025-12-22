# 저축 목표-컬렉션 시스템 리팩토링 PRD v2

## 개요

저축 목표와 실제 집 가격을 분리하여 게이미피케이션 시스템을 단순화합니다.

---

## 의사결정 배경 (Discussion Summary)

### 🎯 기존 문제

1. **집값-저축-컬렉션 삼각관계 복잡성**
   - 집값(5억) ↔ 저축액 ↔ 컬렉션 XP가 모두 얽혀있음
   - 매물 변경 시 이월 계산, anchor_amount 등 복잡한 로직 필요

2. **동기부여 저하**
   - 5억 목표 → 한 Phase당 4,545만원 필요 → 비현실적

3. **예외 케이스 다수**
   - 매물 변경 시 진행중 컬렉션 처리
   - 저축액 이월 및 anchor 기준 계산
   - 목표 금액 검증 등

### 💡 새로운 접근법

**"저축 목표"와 "실제 집 가격"을 완전히 분리**

```
┌────────────────────────────────┐     ┌────────────────────────────────┐
│  저축 목표 (사용자 설정)         │     │  실제 집 가격 (참조용)          │
│  - 컬렉션 XP/Phase 계산 기준     │ ⊥   │  - Gap 정보 표시용              │
│  - 저축 완료 판정 기준           │     │  - DSR/대출 한도 계산용          │
│  - 게이미피케이션 핵심           │     │  - 게이미피케이션과 무관          │
└────────────────────────────────┘     └────────────────────────────────┘
```

### ✅ 합의된 설계 결정

1. **저축 목표 = 컬렉션 기준**: `targetAmount`를 "저축 목표"로 재해석
2. **집값 분리**: 실제 집 가격은 매물 정보에서 조회, 게이미피케이션과 분리
3. **목표 달성 = 1 컬렉션 완성**: 저축 목표 달성 시 컬렉션 1개 완성
4. **연속 컬렉션**: 새로운 저축 목표 설정 → 새 dream_home 생성
5. **목표 변경 제한**: 진행 중(`current_saved_amount > 0`)에는 목표 변경 불가
6. **매물 연결/해제**: `apt_seq`는 참조 정보이며 연결/해제가 가능

---

## 1. 핵심 변경 요약

| 항목 | AS-IS | TO-BE |
|-----|-------|-------|
| `targetAmount` 의미 | 실제 집 가격 기준 | **저축 목표** (사용자 설정) |
| Phase 계산 | `saved / 집값 * 11` | `saved / 저축목표 * 11` (동일 로직) |
| 컬렉션 완료 | 집값 달성 시 | **저축 목표 달성 시** |
| 집값 활용 | Phase 계산에 사용 | **참조 정보만** (Gap 표시) |
| 매물 변경 시 | 복잡한 이월 계산 | **저축 목표에 영향 없음** |

---

## 2. 데이터 모델 변경

### 2.1 dream_home 테이블 (재해석)

| 컬럼 | 기존 의미 | 새 의미 |
|-----|----------|--------|
| `target_amount` | 실제 집 가격 기반 | **저축 목표** (사용자 설정) |
| `apt_seq` | 목표 매물 | **참조 매물** (선택) |
| `current_saved_amount` | 저축 누적액 | 동일 |
| `status` | ACTIVE/COMPLETED | 동일 |

**스키마 변경 없음!** 기존 컬럼의 의미만 재해석합니다.
추가로, `user_collection`은 **dream_home당 최대 1건**만 생성되도록 백엔드에서 중복 생성을 방지합니다.

### 2.2 ER 다이어그램 (TO-BE)

```
┌─────────────────────────┐
│       dream_home        │
├─────────────────────────┤
│ dream_home_id (PK)      │
│ user_id (FK)            │
│ apt_seq (FK, nullable)  │ ← 참조 매물 (선택사항)
│ target_amount           │ ← 저축 목표 (컬렉션 기준)
│ current_saved_amount    │
│ status                  │
│ ...                     │
└─────────────────────────┘
           │
           │ 1:0..1 (저축 목표 달성 시 1개)
           ↓
┌─────────────────────────┐
│     user_collection     │
├─────────────────────────┤
│ collection_id (PK)      │
│ user_id (FK)            │
│ dream_home_id (FK)      │
│ theme_id                │
│ total_saved             │
│ completed_at            │
│ ...                     │
└─────────────────────────┘
```

---

## 3. 저축 목표 설정 플로우

### 3.1 목표 설정 시나리오

#### Case 1: 매물 선택과 함께 목표 설정

```
1. 사용자가 "강남 오피스텔" 선택 (시세 5억원)
2. 시스템이 권장 저축 목표 제안:
   - 계약금 30%: 1.5억원
   - 자기자본 50%: 2.5억원
   - 또는 직접 입력
3. 사용자가 "1억원" 선택
4. dream_home 생성:
   - apt_seq: 강남 오피스텔
   - target_amount: 100,000,000 (1억)
```

#### Case 2: 매물 없이 순수 저축 목표

```
1. 사용자가 "저축 목표만 설정" 선택
2. 목표 금액 직접 입력: 5천만원
3. dream_home 생성:
   - apt_seq: NULL
   - target_amount: 50,000,000 (5천만원)
```

### 3.2 UI 와이어프레임

```
┌─────────────────────────────────────────────────────────────┐
│  🏠 목표 설정                                                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  선택한 매물: 강남 오피스텔                                    │
│  시세: 5억원                                                  │
│                                                               │
│  ─────────────────────────────────────────                   │
│                                                               │
│  📌 저축 목표를 설정해 주세요                                  │
│                                                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐             │
│  │ 5천만원  │ │  1억원   │ │ 1.5억원  │ │ 직접입력 │             │
│  │ (10%)   │ │ (20%)   │ │ (30%)   │ │         │             │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘             │
│       ↑ 추천                                                  │
│                                                               │
│  💡 목표를 달성하면 컬렉션이 완성돼요!                          │
│     더 큰 목표를 세우면 나중에 새 컬렉션을 시작할 수 있어요.     │
│                                                               │
│  ────────────────────────────────────────────────            │
│                                                               │
│  📅 목표 날짜: [2025년 12월]                                   │
│                                                               │
│                           [시작하기]                          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Phase 계산 로직 (변경 없음)

### 4.1 기존 로직 유지

```java
// CollectionService.java
private int calculatePhase(long cumulativeAmount, long targetAmount) {
    if (targetAmount <= 0) return 1;
    
    long numerator = cumulativeAmount * TOTAL_PHASES;
    int phase = (int) (numerator / targetAmount) + 1;
    return Math.max(1, Math.min(phase, TOTAL_PHASES));
}
```

**변경 없음!** `targetAmount`가 "저축 목표"로 재해석되므로 로직 그대로 동작.

### 4.2 예시

```
저축 목표: 1억원
현재 저축: 3천만원
Phase = (30,000,000 * 11) / 100,000,000 + 1 = 4.3 → Phase 4

11단계 Phase별 금액:
- Phase 1: 0 ~ 909만원
- Phase 2: 909만원 ~ 1,818만원
- ...
- Phase 11: 9,091만원 ~ 1억원 (완료)
```

---

## 5. 컬렉션 완료 판정 (변경 없음)

### 5.1 기존 로직 유지

```java
// DreamHomeService.java
private boolean checkAndUpdateCompletion(DreamHome dreamHome, long newSavedAmount, Long userId) {
    long targetAmount = nullToZero(dreamHome.getTargetAmount());
    boolean isCompleted = newSavedAmount >= targetAmount;  // ← 저축 목표 달성 판정

    if (isCompleted) {
        dreamHomeMapper.updateStatus(dreamHome.getDreamHomeId(), DreamHomeStatus.COMPLETED);
        collectionService.registerOnCompletion(userId, dreamHome, newSavedAmount);
    }
    return isCompleted;
}
```

**변경 없음!** 저축 목표(`targetAmount`) 달성 시 컬렉션 완료.

### 5.2 정책/예외 처리

- **컬렉션 수 제한**: dream_home당 user_collection은 최대 1개만 생성한다.
- **목표 변경 정책**: `current_saved_amount > 0`이면 목표 변경 금지, 0일 때만 허용.
- **매물 연결 해제**: `apt_seq`는 nullable이며, 요청에 `aptSeq: null`을 명시하면 연결 해제.
- **Gap 표시 규칙**: `gap = max(0, price - currentAmount)`로 계산하고 음수는 "초과 달성"으로 표시.
- **추천 목표 옵션**: 매물 선택 시에만 퍼센트 옵션을 노출한다.

---

## 6. 매물 변경 처리 (단순화)

### 6.1 AS-IS (복잡)

```
매물 변경 시:
1. 기존 dream_home 아카이브 (GIVEN_UP)
2. 저축액 이월
3. collection_anchor_amount 설정
4. 새 dream_home 생성
5. 진행중 컬렉션 Phase 초기화 계산
```

### 6.2 TO-BE (단순)

```
매물 변경 시:
1. dream_home.apt_seq만 업데이트
2. 저축 목표(target_amount)는 그대로 유지
3. 컬렉션 진행 상태 영향 없음
```

### 6.3 구현 코드

```java
// DreamHomeService.java - setDreamHome() 수정
@Transactional
public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
    DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);
    
    if (existingDreamHome == null) {
        // Case 1: 최초 설정 - 새 드림홈 생성
        return createNewDreamHome(userId, request);
    }
    
    // Case 2: 기존 드림홈 존재
    // aptSeq가 요청에 포함된 경우에만 업데이트 (null이면 연결 해제)
    if (request.hasAptSeq()) {
        if (!Objects.equals(existingDreamHome.getAptSeq(), request.aptSeq())) {
            // 매물 변경/해제 → apt_seq만 업데이트 (저축 목표에 영향 없음)
            dreamHomeMapper.updateAptSeq(existingDreamHome.getDreamHomeId(), request.aptSeq());
            log.info("Property changed for dreamHome: {}", existingDreamHome.getDreamHomeId());
        }
    }
    
    // 저축 목표 변경 (저축액 0일 때만 허용)
    if (request.targetAmount() != null && !request.targetAmount().equals(existingDreamHome.getTargetAmount())) {
        // 저축 목표 변경 → 진행 중이면 거부
        validateTargetAmountChange(existingDreamHome, request.targetAmount());
        dreamHomeMapper.updateTargetAmount(existingDreamHome.getDreamHomeId(), request.targetAmount());
    }
    
    // ... 기타 업데이트
    
    return DreamHomeSetResponse.from(updatedDreamHome, apartment, latestDealPrice);
}

// 저축 목표 변경 검증: 현재 저축액보다 작은 목표로 변경 불가
private void validateTargetAmountChange(DreamHome existing, Long newTarget) {
    if (existing.getCurrentSavedAmount() > 0) {
        throw new BusinessException(ErrorCode.TARGET_CHANGE_NOT_ALLOWED,
            "저축 진행 중에는 목표를 변경할 수 없습니다.");
    }
    if (newTarget < existing.getCurrentSavedAmount()) {
        throw new BusinessException(ErrorCode.TARGET_LESS_THAN_SAVED,
            "저축 목표는 현재 저축액보다 작을 수 없습니다.");
    }
}
```

---

## 7. 컬렉션 완료 후 새 목표 설정

### 7.1 플로우

```
1. 저축 목표 1억 달성 → 컬렉션 완료!
2. 컬렉션 완료 모달 표시
3. "새 목표 설정하기" 버튼 클릭
4. 새 저축 목표 입력 (예: 1.5억)
5. 새 dream_home 생성 (저축액 0부터 시작)
```

### 7.2 구현 코드

```java
// DreamHomeService.java
// 기존 findActiveByUserId는 status='ACTIVE'만 조회
// → COMPLETED 드림홈 있어도 null 반환 → 자연스럽게 새 레코드 생성

// 새 목표 설정 시
public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
    DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);
    
    if (existingDreamHome == null) {
        // COMPLETED 상태이거나 최초 → 새 드림홈 생성
        return createNewDreamHome(userId, request);  // 저축액 0부터 시작
    }
    // ...
}
```

---

## 8. 프론트엔드 변경사항

### 8.1 DreamHomeSetModal.vue 수정

**기존**: 집값 기반 목표 금액 자동 계산
**변경**: 사용자가 저축 목표 직접 선택

```javascript
// 권장 목표 금액 옵션 생성
const suggestedTargets = computed(() => {
    const propertyPrice = selectedProperty.value?.price || 0
    const baseTargets = [
        { label: '5천만원', value: 50_000_000 },
        { label: '1억원', value: 100_000_000 },
        { label: '직접 입력', value: null }
    ]

    if (propertyPrice > 0) {
        baseTargets.splice(2, 0, { label: `30% (${formatMoney(propertyPrice * 0.3)})`, value: propertyPrice * 0.3 })
    }
    return baseTargets
})
```

### 8.2 대시보드 표시 변경

**MainGoalCard.vue**:

```vue
<template>
  <div class="main-goal-card">
    <!-- 저축 진행률 (게이미피케이션) -->
    <div class="savings-progress">
      <h3>저축 진행률</h3>
      <ProgressBar :value="savingsProgress" />
      <p>{{ formatMoney(currentAmount) }} / {{ formatMoney(targetAmount) }}</p>
      <p>Phase {{ currentPhase }} / 11</p>
    </div>
    
    <!-- 목표 집과의 Gap (참조 정보) -->
    <div class="property-gap" v-if="linkedProperty">
      <h4>{{ linkedProperty.name }}</h4>
      <p>시세: {{ formatMoney(linkedProperty.price) }}</p>
      <p class="gap-text">
        남은 금액: {{ formatMoney(Math.max(0, linkedProperty.price - currentAmount)) }}
      </p>
    </div>
  </div>
</template>
```

### 8.3 컬렉션 완료 모달

**CollectionCompleteModal.vue** (신규):

```vue
<template>
  <Modal v-model="isOpen">
    <div class="completion-content">
      <h2>🎉 축하합니다!</h2>
      <CrystalBall :themeCode="themeCode" />
      <p>저축 목표 {{ formatMoney(targetAmount) }}를 달성했어요!</p>
      
      <div class="actions">
        <button @click="viewCollection">컬렉션 보기</button>
        <button @click="setNewGoal" class="primary">새 목표 설정하기</button>
      </div>
    </div>
  </Modal>
</template>
```

---

## 9. API 변경사항

### 9.1 GET /api/dashboard 응답 확장

```json
{
  "goal": {
    "targetAmount": 100000000,        // 저축 목표
    "currentAmount": 35000000,        // 현재 저축액
    "achievementRate": 35.0,          // 진행률
    "phase": 4,                       // 현재 Phase
    "linkedProperty": {               // 🆕 연결된 매물 (참조용)
      "aptSeq": "12345",
      "name": "강남 오피스텔",
      "price": 500000000,             // 실제 시세
      "gap": 465000000                // 시세 - 현재 저축액
    }
  }
}
```

### 9.2 POST /api/dream-home 요청 변경

```json
{
  "aptSeq": "12345",          // 선택 (매물 연결)
  "targetAmount": 100000000,  // 필수 (저축 목표)
  "targetDate": "2025-12-31",
  "monthlyGoal": 5000000,
  "themeId": 2
}
```
`aptSeq`를 `null`로 보내면 연결 해제, 필드 자체가 없으면 기존 값 유지.

### 9.3 POST /api/dream-home/savings 응답 (변경 없음)

```json
{
  "dreamHomeStatus": {
    "currentSavedAmount": 40000000,
    "targetAmount": 100000000,
    "achievementRate": 40.0,
    "isCompleted": false
  },
  "expChange": 50,
  "levelStatus": { ... }
}
```

---

## 10. 구현 체크리스트

### Phase 1: 백엔드 (우선순위: 높음)

- [ ] `DreamHomeService.setDreamHome()` 수정
  - [ ] `targetAmount`를 저축 목표로 직접 받도록 변경
  - [ ] 매물 변경 시 `apt_seq`만 업데이트하는 로직
  - [ ] 저축 목표 변경 검증 (현재 저축액보다 작으면 거부)
- [ ] `DreamHomeMapper.xml` 수정
  - [ ] `updateAptSeq()` 메서드 추가
  - [ ] `updateTargetAmount()` 메서드 추가
- [ ] `DashboardService` 수정
  - [ ] 응답에 `linkedProperty.gap` 정보 추가

### Phase 2: 프론트엔드 (우선순위: 중간)

- [ ] `DreamHomeSetModal.vue` 수정
  - [ ] 저축 목표 선택 UI 추가 (추천 옵션 + 직접 입력)
  - [ ] 매물 선택 시 권장 목표 계산
- [ ] `MainGoalCard.vue` 수정
  - [ ] 저축 진행률과 Gap 정보 분리 표시
- [ ] `CollectionCompleteModal.vue` 생성
  - [ ] 목표 달성 시 표시
  - [ ] "새 목표 설정하기" 버튼

### Phase 3: 기존 코드 정리

- [ ] 기존 `collection_anchor_amount` 관련 로직 제거 (PRD v1 내용)
- [ ] 불필요한 이월 계산 로직 제거

### Phase 4: 테스트

- [ ] 저축 목표 설정 테스트
- [ ] Phase 계산 정확성 테스트
- [ ] 컬렉션 완료 → 새 목표 설정 플로우 테스트
- [ ] 매물 변경 시 진행률 유지 테스트
- [ ] 진행 중 목표 변경 제한 테스트
- [ ] 매물 연결 해제(apt_seq null) 테스트
- [ ] Gap 음수 방지 표시 테스트

---

## 11. 테스트 시나리오

### 시나리오 1: 기본 저축 목표 달성

```
1. 사용자가 1억원 저축 목표 설정 (강남 오피스텔 연결)
2. 3천만원 저축 → Phase 4, 진행률 30%
3. 1억원 저축 완료 → 컬렉션 완료!
4. 컬렉션 갤러리에 1개 추가
```

### 시나리오 2: 매물 변경

```
1. 사용자가 1억원 목표로 5천만원 저축 중 (Phase 6)
2. 연결 매물을 "해운대 아파트"로 변경
3. 저축 목표(1억), 진행률(50%), Phase(6) 모두 유지
4. Gap 정보만 새 매물 기준으로 업데이트
```

### 시나리오 3: 컬렉션 완료 후 새 목표

```
1. 1억원 목표 달성 → 컬렉션 1 완료
2. "새 목표 설정하기" 클릭
3. 1.5억원 새 목표 설정
4. 저축액 0부터 다시 시작
5. 기존 컬렉션은 갤러리에 보존
```

### 시나리오 4: 매물 없이 순수 저축

```
1. 매물 선택 없이 5천만원 저축 목표 설정
2. 저축 진행, Phase 계산 정상 동작
3. 목표 달성 시 컬렉션 완료
4. Gap 정보는 표시되지 않음
```

### 시나리오 5: 목표 변경 제한

```
1. 사용자가 1억원 목표로 1천만원 저축
2. 목표 변경 시도 → 거부 (저축 진행 중)
3. 저축액 0일 때만 목표 변경 가능
```

### 시나리오 6: 매물 연결 해제

```
1. 매물 연결 상태로 저축 진행
2. aptSeq = null 요청으로 연결 해제
3. 저축 진행률/Phase 유지, Gap 정보 미표시
```

---

## 12. 마이그레이션

**DB 스키마 변경 없음!**

기존 데이터의 `target_amount`가 집값 기준이었다면:
- 테스트 단계에서는 DB를 초기화하므로 마이그레이션 불필요
- 운영 적용 시에는 별도 마이그레이션/재설정 정책 필요 (현재 범위 제외)

---

## 13. 참고 파일

| 영역 | 파일 |
|-----|------|
| **Backend Service** | `DreamHomeService.java`, `DashboardService.java` |
| **Backend Mapper** | `DreamHomeMapper.xml` |
| **Frontend Modal** | `DreamHomeSetModal.vue` |
| **Frontend Dashboard** | `MainGoalCard.vue` |
| **Frontend Collection** | `CollectionView.vue` |

---

## 14. 기존 PRD와의 차이점

| 항목 | PRD v1 (N:1 구조) | PRD v2 (저축 목표 분리) |
|-----|-------------------|----------------------|
| 복잡도 | 높음 | **낮음** |
| 스키마 변경 | `collection_anchor_amount` 추가 | **없음** |
| 매물 변경 처리 | 새 dream_home + 이월 | **apt_seq만 업데이트** |
| Phase 계산 | `(saved - anchor) / target` | **기존 로직 유지** |
| 컬렉션 완료 판정 | 복잡한 조건 | **기존 로직 유지** |
| 목표 변경 정책 | 제한 없음 | **저축 진행 중 변경 금지** |

---

*Last Updated: 2024-12-22*
*Version: 2.0*
