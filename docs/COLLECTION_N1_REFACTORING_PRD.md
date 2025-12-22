# 컬렉션 시스템 N:1 구조 리팩토링 PRD

---

## 의사결정 배경 (Discussion Summary)

### 🎯 문제 인식

저축 목표와 "키우는 집" 게이미피케이션 시스템 간의 불일치가 발견되었습니다.

**현재 상황:**
- 실제 집 매물 목표(예: 5억원)와 키우는 집 목표가 **1:1 대응**
- 11단계 Phase 시스템에서 한 단계당 필요 금액: `5억 / 11 = 약 4,545만원`
- **문제**: 한 단계 올리려면 수천만원이 필요해 사용자 동기부여 저하

### 💡 해결 방향 논의

| 질문 | 결정 | 이유 |
|-----|-----|-----|
| **매물 변경 시 저축액 처리?** | 저축액 유지 + 진행중 컬렉션 초기화 | 힘들게 모은 돈은 보존, 새 매물에서 새로운 시작 |
| **컬렉션 완료 후 새 목표 설정?** | 새 dream_home 레코드 생성 | 기존 COMPLETED 드림홈과 컬렉션 관계 유지 |
| **N:1 구조 구현 방식?** | collection_target 필드 추가 (5천만원 고정) | MVP로 빠른 검증, 추후 사용자 선택으로 확장 가능 |

### 🔍 현재 코드 분석 결과

**발견된 문제점:**

1. **매물 변경 시 저축 히스토리 혼재**
   ```java
   // DreamHomeService.java:104-115
   // 동일한 dreamHomeId에서 aptSeq만 변경
   // → 이전 매물 저축 히스토리가 새 매물에 섞임
   ```

2. **COMPLETED 드림홈 재활용 문제**
   ```java
   // 현재: setDreamHome() 호출 시 기존 레코드 UPDATE
   // 문제: user_collection.dream_home_id가 가리키는 드림홈이 ACTIVE로 변경됨
   ```

3. **Phase 계산이 실제 목표 기준**
   ```java
   // CollectionService.java:523
   long numerator = cumulativeAmount * TOTAL_PHASES;
   int phase = (int) (numerator / targetAmount) + 1;
   // → 5억 목표면 한 Phase에 4천만원 필요
   ```

### ✅ 합의된 설계 결정

1. **저축액 유지 + 컬렉션 초기화**: 매물 변경 시 돈은 이어가되, 새 집에서 Phase 1부터 시작
2. **새 dream_home 레코드 생성**: 매물 변경/컬렉션 완료 시 기존 레코드는 보존
3. **5천만원 단위 컬렉션**: Phase당 약 450만원으로 적절한 동기부여
4. **컬렉션 순번 기준**: dream_home 단위로 관리 (사용자 전체 누적은 별도 통계로만 제공)
5. **이월 기준값 저장**: 매물 변경 시 진행률 초기화를 위해 `collection_anchor_amount` 저장

---

## 1. 개요

### 1.1 배경
현재 시스템은 **실제 매물 목표**와 **키우는 집(컬렉션)**이 1:1로 매핑되어 있습니다.
실제 목표 금액이 너무 크면(예: 5억원) 한 Phase에 수천만원이 필요해 사용자 동기부여가 저하됩니다.

### 1.2 목표
1. **N:1 구조 도입**: 하나의 실제 목표에 여러 개의 미니 컬렉션을 완성할 수 있도록 변경
2. **매물 변경 시 데이터 일관성 보장**: 저축액은 유지하되 진행중 컬렉션은 초기화
3. **컬렉션 완료 후 새 목표 설정 시 새 dream_home 레코드 생성**

### 1.3 핵심 변경 요약

| 항목 | AS-IS | TO-BE |
|-----|-------|-------|
| 목표-컬렉션 관계 | 1:1 | N:1 (여러 컬렉션 → 하나의 실제 목표) |
| Phase 계산 기준 | `targetAmount` (실제 목표) | `collectionTarget` (5천만원 고정) |
| 매물 변경 시 | 동일 dream_home 레코드 UPDATE | 새 dream_home 레코드 INSERT |
| 컬렉션 완료 후 | 기존 레코드 재활용 | 새 dream_home 레코드 생성 |

---

## 2. 상세 요구사항

### 2.1 컬렉션 목표 금액 (Collection Target)

```java
// 상수 정의
public static final long DEFAULT_COLLECTION_TARGET = 50_000_000L; // 5천만원
```

- **5천만원 고정** (MVP 단계)
- Phase당 약 454만원 (`50,000,000 / 11 = 4,545,454원`)
- 추후 사용자 선택 옵션으로 확장 가능

### 2.2 Phase 계산 로직 변경

**AS-IS:**
```java
// CollectionService.java:523
long numerator = cumulativeAmount * TOTAL_PHASES;
int phase = (int) (numerator / targetAmount) + 1;
```

**TO-BE:**
```java
// 현재 컬렉션 내 진행 금액 계산 (dream_home 기준)
long collectionTarget = DEFAULT_COLLECTION_TARGET;
long effectiveSaved = cumulativeAmount - collectionAnchorAmount;
long completedCollectionAmount = (effectiveSaved / collectionTarget) * collectionTarget;
long amountInCurrentCollection = effectiveSaved - completedCollectionAmount;

// Phase 계산 (현재 컬렉션 기준)
int phase = (int) ((amountInCurrentCollection * TOTAL_PHASES) / collectionTarget) + 1;
return Math.max(1, Math.min(phase, TOTAL_PHASES));
```

### 2.3 컬렉션 완료 판정 로직 변경

**AS-IS:**
```java
// DreamHomeService.java:244
boolean isCompleted = newSavedAmount >= targetAmount;
```

**TO-BE:**
```java
// 컬렉션 완료 체크 (5천만원 단위)
long collectionTarget = DEFAULT_COLLECTION_TARGET;
long previousEffectiveSaved = previousSavedAmount - collectionAnchorAmount;
long newEffectiveSaved = newSavedAmount - collectionAnchorAmount;
int previousCollectionCount = (int) (previousEffectiveSaved / collectionTarget);
int newCollectionCount = (int) (newEffectiveSaved / collectionTarget);

boolean isCollectionCompleted = newCollectionCount > previousCollectionCount;
boolean isDreamHomeCompleted = newSavedAmount >= targetAmount;

if (isCollectionCompleted) {
    // 한 번에 여러 컬렉션 완료 가능 → 누락 없이 모두 등록
    for (int order = previousCollectionCount + 1; order <= newCollectionCount; order++) {
        collectionService.registerOnCollectionComplete(
            userId, dreamHome, newSavedAmount, order
        );
    }
}

if (isDreamHomeCompleted) {
    // 최종 목표 달성 → 드림홈 COMPLETED 처리
    dreamHomeMapper.updateStatus(dreamHome.getDreamHomeId(), DreamHomeStatus.COMPLETED);
}
```

### 2.4 정책/예외 처리

- **컬렉션 순번 계산 기준**: `user_collection`에서 `dream_home_id` 기준으로 카운트한다.
- **다중 컬렉션 완료**: 한 번의 저축으로 여러 컬렉션이 완료되면 모두 자동 등록한다.
- **매물 변경 시 목표 검증**: 이월 금액이 새 목표 이상이면 변경을 거부하고 더 큰 목표를 요구한다.
- **진행률 초기화 기준**: `collection_anchor_amount`를 기준으로 Phase/진행률을 계산한다.

---

## 3. 매물 변경 시 처리

### 3.1 시나리오

```
사용자가 A아파트(5억) 저축 중 → 1억 저축 완료 (컬렉션 2개 완성)
→ B아파트(3억)로 목표 변경
```

### 3.2 처리 방식

| 항목 | 동작 |
|-----|-----|
| **저축 금액** | 유지 (1억원 그대로) |
| **완료 컬렉션** | 유지 (2개 그대로, user_collection에 저장됨) |
| **진행중 컬렉션** | 초기화 (새 매물 기준으로 Phase 1부터 시작) |
| **dream_home 레코드** | **새로 생성** (기존 레코드는 GIVEN_UP 또는 soft delete) |

### 3.3 구현 변경

```java
// DreamHomeService.java - setDreamHome() 수정
@Transactional
public DreamHomeSetResponse setDreamHome(Long userId, DreamHomeSetRequest request) {
    Apartment apartment = findApartmentOrThrow(request.aptSeq());
    Long latestDealPrice = resolveLatestDealPrice(apartment);
    long targetAmount = resolveValidatedTargetAmount(userId, request, latestDealPrice);
    
    DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);
    
    // 테마 선택 시 검증 및 저장
    if (request.themeId() != null) {
        validateAndSaveTheme(userId, request.themeId());
    }
    
    DreamHome dreamHome;
    if (existingDreamHome == null) {
        // Case 1: 최초 설정
        dreamHome = createNewDreamHome(userId, request, targetAmount, 0L);
    } else if (existingDreamHome.getAptSeq().equals(request.aptSeq())) {
        // Case 2: 동일 매물 - 목표 금액/날짜만 변경
        dreamHome = updateExistingDreamHome(existingDreamHome, request, targetAmount);
    } else {
        // Case 3: 다른 매물로 변경 - 새 레코드 생성 (저축액 이전)
        archiveExistingDreamHome(existingDreamHome); // GIVEN_UP 처리
        long carryOverAmount = nullToZero(existingDreamHome.getCurrentSavedAmount());
        validateTargetAmountGreaterThanCarryOver(targetAmount, carryOverAmount);
        dreamHome = createNewDreamHome(userId, request, targetAmount, carryOverAmount);
    }
    
    return DreamHomeSetResponse.from(dreamHome, apartment, latestDealPrice);
}

// 기존 드림홈 아카이브 (매물 변경 시)
private void archiveExistingDreamHome(DreamHome existing) {
    dreamHomeMapper.updateStatus(existing.getDreamHomeId(), DreamHomeStatus.GIVEN_UP);
    log.info("Dream home archived due to property change. dreamHomeId: {}", existing.getDreamHomeId());
}

// 새 드림홈 생성 (저축액 이전 + 진행률 초기화 지원)
private DreamHome createNewDreamHome(Long userId, DreamHomeSetRequest request, long targetAmount, long carryOverAmount) {
    DreamHome newDreamHome = buildDreamHome(null, userId, request, targetAmount, carryOverAmount);
    newDreamHome.setCollectionAnchorAmount(carryOverAmount);
    dreamHomeMapper.insert(newDreamHome);
    log.info("Dream home created. dreamHomeId: {}, aptSeq: {}, carryOver: {}", 
        newDreamHome.getDreamHomeId(), request.aptSeq(), carryOverAmount);
    return newDreamHome;
}
```

---

## 4. 컬렉션 완료 후 새 목표 설정

### 4.1 시나리오

```
사용자가 A아파트 목표 5억 달성 (COMPLETED)
→ 다음 목표로 B아파트 선택
```

### 4.2 처리 방식

- 기존 COMPLETED 드림홈은 **그대로 유지**
- `user_collection.dream_home_id`는 COMPLETED 상태의 드림홈을 계속 참조
- 새 드림홈 레코드 생성 (저축액 0부터 시작)

### 4.3 구현 변경

```java
// DreamHomeService.java - 조회 로직 수정
DreamHome existingDreamHome = dreamHomeMapper.findActiveByUserId(userId);
// findActiveByUserId는 status='ACTIVE'만 조회하므로, 
// COMPLETED 드림홈이 있어도 null 반환 → 자연스럽게 새 레코드 생성
```

**현재 코드가 이미 `ACTIVE`만 조회**하므로 추가 수정 불필요!
다만, 저축액 이전 로직이 필요 없는 경우(목표 완료 후 새 시작)는 0부터 시작.

---

## 5. 프론트엔드 변경사항

### 5.1 진행률 표시 변경

**파일**: `src/stores/dreamHomeStore.js`, `src/components/dashboard/bento/MainGoalCard.vue`

```javascript
// AS-IS
const achievementRate = computed(() => {
    return ((currentAmount.value / targetAmount.value) * 100).toFixed(1)
})

// TO-BE
const COLLECTION_TARGET = 50_000_000 // 5천만원

const currentCollectionProgress = computed(() => {
    const amountInCollection = currentAmount.value % COLLECTION_TARGET
    return ((amountInCollection / COLLECTION_TARGET) * 100).toFixed(1)
})

const completedCollectionCount = computed(() => {
    return Math.floor(currentAmount.value / COLLECTION_TARGET)
})

// 전체 목표 달성률 (별도 표시)
const overallProgress = computed(() => {
    return ((currentAmount.value / targetAmount.value) * 100).toFixed(1)
})
```

### 5.2 컬렉션 완료 알림 모달

**새 파일**: `src/components/modals/CollectionCompleteModal.vue`

```
┌─────────────────────────────────────────────────────────────┐
│                                                               │
│                    🎉 컬렉션 완성! 🎉                          │
│                                                               │
│           ┌───────────────────────────────────────┐           │
│           │                                       │           │
│           │         🏠 완성된 집 이미지           │           │
│           │                                       │           │
│           └───────────────────────────────────────┘           │
│                                                               │
│                  "3번째 드림홈을 완성했어요!"                   │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  💰 5,000만원 저축 완료  ·  🏆 총 1.5억 달성         │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│   [🎨 새 테마 선택하기]        [📍 계속 저축하기]             │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 컬렉션 갤러리 업데이트

**파일**: `src/views/CollectionView.vue`

- 완료된 컬렉션에 "완료" 배지 표시
- 진행중 컬렉션에 "3번째 도전 중" 표시
- 같은 매물에서 완료한 컬렉션들을 그룹핑 표시 (선택적)

---

## 6. 데이터베이스 스키마

### 6.1 dream_home 테이블 (필드 추가)

`status` ENUM에 `GIVEN_UP` 추가 필요.
또한, 진행률 초기화를 위해 기준 금액을 저장한다.

```sql
-- DreamHomeStatus enum 확인
-- ACTIVE, COMPLETED, GIVEN_UP (매물 변경 시)

ALTER TABLE dream_home
ADD COLUMN collection_anchor_amount BIGINT DEFAULT 0 COMMENT '컬렉션 진행률 기준 저축액';
```

### 6.2 user_collection 테이블 (필드 추가)

```sql
ALTER TABLE user_collection 
ADD COLUMN collection_order INT DEFAULT 1 COMMENT '해당 드림홈 내 N번째 컬렉션';
```

### 6.3 상수 테이블 또는 설정 (선택)

MVP에서는 코드 상수로 관리:
```java
public static final long DEFAULT_COLLECTION_TARGET = 50_000_000L;
```

---

## 7. API 변경사항

### 7.1 POST /api/dream-home/savings 응답 확장

```json
{
  "dreamHomeStatus": {
    "currentSavedAmount": 55000000,
    "targetAmount": 500000000,
    "achievementRate": 11.0,
    "isCompleted": false
  },
  "collectionStatus": {
    "isCollectionCompleted": true,          // 🆕 컬렉션 완료 여부
    "completedCollectionCount": 2,          // 🆕 완료한 컬렉션 수
    "currentCollectionOrder": 3,            // 🆕 진행중 컬렉션 순번
    "currentCollectionProgress": 10.0,      // 🆕 현재 컬렉션 진행률
    "amountInCurrentCollection": 5000000    // 🆕 현재 컬렉션 내 저축액
  },
  "expChange": 500,
  "levelStatus": { ... }
}
```

### 7.2 GET /api/collection 응답 확장

```json
{
  "collections": [
    {
      "collectionId": 3,
      "collectionOrder": 3,           // 🆕 몇 번째 컬렉션인지
      "themeCode": "MODERN",
      "propertyName": "강남 오피스텔",
      "completedAt": "2024-06-15T10:30:00"
    }
  ],
  "inProgress": {
    "dreamHomeId": 5,
    "collectionOrder": 4,             // 🆕 진행중인 컬렉션 순번
    "currentProgress": 45.5,          // 현재 컬렉션 진행률
    "amountInCollection": 22750000    // 현재 컬렉션 내 저축액
  }
}
```

---

## 8. 구현 체크리스트

### Phase 1: 백엔드 핵심 로직 (우선순위: 높음)

- [ ] `DreamHomeStatus` enum에 `GIVEN_UP` 추가
- [ ] `DreamHomeService.setDreamHome()` 매물 변경 처리 로직 수정
- [ ] `DreamHomeService.recordSavings()` 컬렉션 완료 판정 로직 추가
- [ ] `CollectionService.registerOnCollectionComplete()` 메서드 추가
- [ ] `CollectionService.calculatePhase()` 로직 수정 (collectionTarget 기준)
- [ ] `DreamHome`에 `collectionAnchorAmount` 추가 및 저장
- [ ] 매물 변경 시 목표 금액 검증 (이월 금액보다 큰 목표만 허용)

### Phase 2: API 응답 확장

- [ ] `SavingsRecordResponse`에 `collectionStatus` 필드 추가
- [ ] `CollectionResponse.InProgressInfo`에 `collectionOrder` 필드 추가

### Phase 3: 프론트엔드

- [ ] `dreamHomeStore.js` 진행률 계산 로직 수정
- [ ] `CollectionCompleteModal.vue` 컴포넌트 생성
- [ ] `SavingsView.vue`에서 컬렉션 완료 시 모달 표시
- [ ] `CollectionView.vue` 완료 배지 및 순번 표시

### Phase 4: 테스트

- [ ] 매물 변경 시 저축액 이전 테스트
- [ ] 컬렉션 완료 자동 등록 테스트
- [ ] Phase 계산 정확성 테스트
- [ ] 여정 리플레이 데이터 정합성 테스트
- [ ] 한 번의 저축으로 다중 컬렉션 완료 테스트

---

## 9. 테스트 시나리오

### 시나리오 1: 기본 컬렉션 완료

```
1. 사용자가 5억 목표 설정
2. 5천만원 저축 → 컬렉션 1 완료 알림
3. 1억원 저축 → 컬렉션 2 완료 알림
4. 컬렉션 갤러리에서 2개 완료 확인
```

### 시나리오 2: 매물 변경

```
1. 사용자가 A아파트(5억) 목표로 7천만원 저축 (컬렉션 1 완료 + 진행중 20%)
2. B아파트(3억)로 변경
3. 저축액 7천만원 유지
4. 컬렉션 1은 유지, 진행중 컬렉션은 Phase 1부터 시작 (2천만원 / 5천만원 = 40%)
```

### 시나리오 3: 최종 목표 달성 후 새 목표

```
1. 사용자가 A아파트 5억 달성 (컬렉션 10개 완료)
2. B아파트 3억 새 목표 설정
3. 저축액 0부터 시작 (이전 저축은 A아파트 히스토리)
4. 컬렉션 갤러리에 A아파트 10개 + B아파트 진행중 표시
```

### 시나리오 4: 한 번에 여러 컬렉션 완료

```
1. 현재 저축액 0원
2. 한 번의 저축으로 1억 2천만원 입력
3. 컬렉션 1~2가 자동 등록되고, 진행중 컬렉션은 3번째
```

---

## 10. 롤백 계획

DB 스키마가 변경되므로 문제 발생 시:

1. `collection_order` 컬럼 제거
2. `GIVEN_UP` 상태 드림홈을 `ACTIVE`로 복원
3. 기존 Phase 계산 로직으로 롤백

현재 DB를 계속 초기화한다고 하셨으므로 마이그레이션 스크립트는 불필요.

---

## 11. 참고 파일

| 영역 | 파일 |
|-----|------|
| **Backend Service** | `DreamHomeService.java`, `CollectionService.java` |
| **Backend Domain** | `DreamHome.java`, `UserCollection.java`, `DreamHomeStatus.java` |
| **Backend Mapper** | `DreamHomeMapper.xml`, `CollectionMapper.xml` |
| **Frontend Store** | `dreamHomeStore.js`, `collectionStore.js` |
| **Frontend Views** | `CollectionView.vue`, `SavingsView.vue`, `JourneyReplayView.vue` |

---

*Last Updated: 2024-12-22*
