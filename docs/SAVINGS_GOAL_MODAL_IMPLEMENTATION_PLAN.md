# 저축 목표 모달 및 테마 선택 기능 구현 계획

사용자가 매물을 선택했을 때 저축 목표를 세울 수 있는 모달 창을 띄우고, 집 테마도 함께 선택할 수 있도록 하는 기능을 구현합니다.

---

## 주요 변경 사항

> **기존 동작 변경**: 현재 "내 집으로 설정" 버튼 클릭 시 즉시 드림홈이 설정되고 대시보드로 이동합니다. 변경 후에는 모달이 먼저 표시되며, 사용자가 목표 금액, 목표 날짜, 테마를 설정한 후 저장해야 합니다.

> **DSR 기반 목표 금액 계산**: 목표 금액은 단순히 `매물가 × 30%`가 아니라, DSR 기반 최대 대출 가능액을 고려하여 계산합니다:
> - **필요 자기자본** = 매물가 - 최대 대출 가능액
> - 사용자가 현재 자산(`currentAssets`)이 있다면 이를 차감
> - 모달에 DSR 등급(SAFE/CAUTION/DANGER)과 대출 한도를 함께 표시

> **테마 이미지 저장소**: Google Cloud Storage(GCS)를 사용하여 테마별 SVG/이미지 파일을 저장합니다.
> - `theme_asset.image_url`에 GCS 공개 URL 저장
> - 프론트엔드에서 URL을 직접 로드

> **DB 스키마 변경 없음**: 현재 `user.selected_theme_id`, `dream_home.apt_seq`, `theme_asset.image_url` 등 필요한 컬럼이 이미 존재합니다.

---

## 1. Frontend - 저축 목표 모달

### [NEW] `SavingsGoalModal.vue`
- 경로: `src/components/modals/SavingsGoalModal.vue`

**주요 기능:**
- 선택된 매물 정보 표시 (아파트명, 위치, 가격)
- DSR 기반 분석 표시:
  - DSR 등급 (SAFE/CAUTION/DANGER) 배지
  - 최대 대출 가능액 표시
  - 필요 자기자본 = 매물가 - 최대 대출 가능액
- 목표 금액 입력 (기본값: 필요 자기자본 - 현재 자산)
- 목표 날짜 선택 (date picker)
- 월 저축 목표 자동 계산 (목표금액 ÷ 남은 개월 수)
- 테마 선택 카드 UI (GCS에서 썸네일 로드)

**Props:**
```javascript
{
  isOpen: Boolean,           // 모달 열림 상태
  property: Object,          // 선택된 매물 정보
  dsrInfo: {                 // DSR 정보
    grade: String,           // 'SAFE' | 'CAUTION' | 'DANGER'
    maxLoanAmount: Number    // 최대 대출 가능액 (만원)
  },
  currentAssets: Number      // 현재 보유 자산 (만원)
}
```

**목표 금액 계산 로직:**
```javascript
const requiredCapital = computed(() => {
  return Math.max(0, props.property.price - props.dsrInfo.maxLoanAmount)
})

const defaultTargetAmount = computed(() => {
  return Math.max(0, requiredCapital.value - props.currentAssets)
})
```

### [MODIFY] `PropertyActions.vue`
- 경로: `src/components/property/detail/PropertyActions.vue`

**변경 내용:**
```javascript
const showSavingsModal = ref(false)

function handleSetAsDreamHome() {
  showSavingsModal.value = true
}

function handleSavingsGoalSubmit(savingsGoal) {
  dreamHomeStore.changeDreamHome({
    aptSeq: props.property.id,
    propertyName: props.property.title,
    location: `${props.property.sido} ${props.property.sigungu}`,
    price: props.property.price,
    targetAmount: savingsGoal.targetAmount,
    targetDate: savingsGoal.targetDate,
    monthlyGoal: savingsGoal.monthlyGoal,
    themeId: savingsGoal.themeId
  })
  showSavingsModal.value = false
  showSuccess(`"${props.property.title}"을(를) 내 집으로 설정했습니다!`, 4000)
  setTimeout(() => { router.push('/') }, 1000)
}
```

---

## 2. Frontend - 테마 이미지 로딩 (GCS)

### 테마 이미지 URL 구조

| theme_code | level | image_url |
|------------|-------|-----------|
| MODERN | 1 | `https://storage.googleapis.com/jipjung-assets/themes/modern/phase1.svg` |
| MODERN | 7 | `https://storage.googleapis.com/jipjung-assets/themes/modern/phase7.svg` |
| HANOK | 1 | `https://storage.googleapis.com/jipjung-assets/themes/hanok/phase1.svg` |

### [MODIFY] `IsometricRoomHero.vue`
- 경로: `src/components/dashboard/IsometricRoomHero.vue`

**변경 전:**
```javascript
const res = await fetch('/phase7.svg')
```

**변경 후:**
```javascript
const themeAssetUrl = authStore.userThemeAsset?.imageUrl || '/phase7.svg'
const res = await fetch(themeAssetUrl)
```

### [NEW] `themeService.js`
- 경로: `src/api/services/themeService.js`

```javascript
export const themeService = {
  async getActiveThemes() {
    const response = await apiClient.get('/themes')
    return response.data
  }
}
```

### [MODIFY] `endpoints.js`
```javascript
export const THEME_ENDPOINTS = {
    LIST: '/themes'
}
```

---

## 3. GCS 설정 가이드

> 테마 이미지 업로드는 사용자가 직접 수행합니다.

### GCS 버킷 설정

```bash
# 버킷 생성 (리전: 서울)
gsutil mb -l asia-northeast3 gs://jipjung-assets

# 공개 읽기 권한 설정
gsutil iam ch allUsers:objectViewer gs://jipjung-assets

# CORS 설정
cat > cors.json << EOF
[
  {
    "origin": ["http://localhost:5173", "https://your-domain.com"],
    "method": ["GET"],
    "responseHeader": ["Content-Type"],
    "maxAgeSeconds": 3600
  }
]
EOF
gsutil cors set cors.json gs://jipjung-assets
```

### 권장 폴더 구조

```
themes/
  modern/
    phase1.svg ~ phase7.svg
    thumbnail.png
  hanok/
    phase1.svg ~ phase7.svg
    thumbnail.png
```

---

## 4. Backend - 테마 API

### [NEW] `ThemeController.java`
```java
@RestController
@RequestMapping("/api/themes")
@RequiredArgsConstructor
public class ThemeController {
    
    private final HouseThemeMapper houseThemeMapper;
    
    @GetMapping
    public ResponseEntity<List<HouseTheme>> getActiveThemes() {
        List<HouseTheme> themes = houseThemeMapper.findAllActive();
        return ResponseEntity.ok(themes);
    }
}
```

### [MODIFY] `HouseThemeMapper.java`
```java
@Mapper
public interface HouseThemeMapper {
    List<HouseTheme> findAllActive();
    HouseTheme findById(@Param("themeId") Integer themeId);
}
```

### [NEW] `HouseThemeMapper.xml`
```xml
<mapper namespace="com.jipjung.project.repository.HouseThemeMapper">
    <select id="findAllActive" resultType="com.jipjung.project.domain.HouseTheme">
        SELECT theme_id, theme_code, theme_name, is_active, created_at, updated_at
        FROM house_theme
        WHERE is_active = TRUE AND is_deleted = FALSE
        ORDER BY theme_id
    </select>
</mapper>
```

---

## 5. Backend - 드림홈 API 확장

### [NEW] `DreamHomeRequest.java`
```java
public record DreamHomeRequest(
    String aptSeq,
    String propertyName,
    String location,
    Long price,
    Long targetAmount,
    Long monthlyGoal,
    LocalDate targetDate,
    Integer themeId
) {}
```

### [MODIFY] `DreamHomeMapper.java`
```java
@Mapper
public interface DreamHomeMapper {
    DreamHome findActiveByUserId(@Param("userId") Long userId);
    int insert(DreamHome dreamHome);
    int update(DreamHome dreamHome);
    int deactivateByUserId(@Param("userId") Long userId);
}
```

### [NEW] `DreamHomeService.java`
```java
@Service
@RequiredArgsConstructor
public class DreamHomeService {
    
    private final DreamHomeMapper dreamHomeMapper;
    private final UserMapper userMapper;
    
    @Transactional
    public DreamHome createOrUpdateDreamHome(Long userId, DreamHomeRequest request) {
        dreamHomeMapper.deactivateByUserId(userId);
        
        DreamHome dreamHome = DreamHome.builder()
            .userId(userId)
            .aptSeq(request.aptSeq())
            .targetAmount(request.targetAmount())
            .targetDate(request.targetDate())
            .monthlyGoal(request.monthlyGoal())
            .currentSavedAmount(0L)
            .startDate(LocalDate.now())
            .status(DreamHomeStatus.ACTIVE)
            .build();
        
        dreamHomeMapper.insert(dreamHome);
        
        if (request.themeId() != null) {
            userMapper.updateSelectedTheme(userId, request.themeId());
        }
        
        return dreamHome;
    }
}
```

### [MODIFY] `UserMapper.java`
```java
int updateSelectedTheme(@Param("userId") Long userId, @Param("themeId") Integer themeId);
```

---

## 6. DB 초기 데이터

```sql
-- house_theme 테이블
INSERT INTO house_theme (theme_code, theme_name, is_active) VALUES
('MODERN', '모던 하우스', TRUE),
('HANOK', '한옥', TRUE),
('CASTLE', '캐슬', TRUE);

-- theme_asset 테이블 (GCS 업로드 후 URL 수정)
INSERT INTO theme_asset (theme_id, level, image_url) VALUES
(1, 1, 'https://storage.googleapis.com/jipjung-assets/themes/modern/phase1.svg'),
(1, 2, 'https://storage.googleapis.com/jipjung-assets/themes/modern/phase2.svg'),
-- ... 생략 ...
(1, 7, 'https://storage.googleapis.com/jipjung-assets/themes/modern/phase7.svg');
```

---

## 파일 변경 요약

| 레이어 | 파일 | 변경 유형 |
|--------|------|-----------|
| Frontend | `SavingsGoalModal.vue` | NEW |
| Frontend | `PropertyActions.vue` | MODIFY |
| Frontend | `IsometricRoomHero.vue` | MODIFY |
| Frontend | `themeService.js` | NEW |
| Frontend | `endpoints.js` | MODIFY |
| Backend | `ThemeController.java` | NEW |
| Backend | `HouseThemeMapper.java` | MODIFY |
| Backend | `HouseThemeMapper.xml` | NEW |
| Backend | `DreamHomeMapper.java` | MODIFY |
| Backend | `DreamHomeService.java` | NEW |
| Backend | `DreamHomeRequest.java` | NEW |
| Backend | `UserMapper.java` | MODIFY |

---

## 검증 계획

### 백엔드 테스트 (Swagger)
1. `GET /api/themes` 테스트 → 활성 테마 목록 반환 확인
2. `PUT /api/users/dream-home` 테스트 → 드림홈 생성 확인

### 프론트엔드 테스트
1. 매물 상세 → "내 집으로 설정" 클릭 → 모달 표시 확인
2. 모달에서 DSR 정보, 목표 금액, 테마 선택 확인
3. 저장 후 대시보드에서 반영 확인
