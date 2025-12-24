# 집-중 (Jip-joong) REST API 명세서

## 개요

**프로젝트**: 감성 저축 게이미피케이션 앱
**API 버전**: v1.0
**Base URL**: `http://localhost:8080/api`
**인증 방식**: JWT Bearer Token
**총 API 개수**: 29개 (구현 완료: 29개 | 미구현: 0개)
**현재 구현 단계**: Phase 3 - AI 매니저 및 드림홈/컬렉션 통합 완료

---

## API 요약

### ✅ 구현 완료 (29개)

| 카테고리 | 개수 | 엔드포인트 | 설명 |
|---------|------|----------|------|
| **인증** | 3 | POST /api/auth/signup, POST /api/auth/login, POST /api/auth/logout | 회원가입, 로그인, 로그아웃 |
| **사용자** | 4 | POST /api/users/onboarding, POST /api/users/profile, DELETE /api/users/account, PUT /api/users/furniture-progress | 온보딩, 프로필, 회원탈퇴, 가구 배치 저장 |
| **아파트** | 6 | GET /api/apartments, GET /api/apartments/{aptSeq}, POST /api/apartments/favorites, GET /api/apartments/favorites, DELETE /api/apartments/favorites/{id}, GET /api/apartments/regions/{regionName}/coordinates | 아파트 조회, 관심 관리, 지역 좌표 |
| **DSR** | 1 | POST /api/simulation/dsr | DSR PRO 시뮬레이션 |
| **AI 매니저** | 4 | POST /api/ai-manager/analyze, POST /api/ai-manager/confirm, POST /api/ai-manager/judgment, GET /api/ai-manager/history | 지출 분석, 데이터 확인, 판결, 내역 조회 |
| **드림홈** | 2 | POST /api/dream-home, POST /api/dream-home/savings | 드림홈 설정, 저축 기록 |
| **컬렉션** | 4 | GET /api/collections, GET /api/collections/{id}/journey, GET /api/collections/in-progress/journey, PUT /api/collections/{id}/main-display | 컬렉션 목록, 여정 조회, 대표 설정 |
| **대시보드** | 1 | GET /api/users/dashboard | 대시보드 통합 데이터 |
| **스트릭** | 3 | GET /api/streak/reward, POST /api/streak/reward, GET /api/streak/milestones | 마일스톤 보상 조회/수령, 상태 조회 |
| **테마** | 1 | GET /api/themes | 하우스 테마 목록 |

---

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

---

# 1. 인증 API (3개) ✅

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

## 1-2. POST /api/auth/login
**로그인**

### 요청
```json
{
  "email": "user@example.com",
  "password": "Test1234!@"
}
```

## 1-3. POST /api/auth/logout
**로그아웃**

---

# 2. 사용자 API (4개) ✅

## 2-1. POST /api/users/onboarding
**온보딩 정보 저장**

### 요청
```json
{
  "birthYear": 1995,
  "annualIncome": 50000000,
  "existingLoanMonthly": 500000,
  "currentAssets": 30000000,
  "preferredAreas": ["강남구", "서초구"]
}
```

## 2-2. POST /api/users/profile
**프로필 수정**

### 요청
```json
{
  "nickname": "건축왕2세",
  "annualIncome": 60000000,
  "existingLoanMonthly": 400000
}
```

## 2-3. DELETE /api/users/account
**회원탈퇴**

### 요청
```json
{
  "password": "Test1234!@"
}
```

## 2-4. PUT /api/users/furniture-progress
**인테리어 진행 상태 동기화**

클라이언트에서 계산된 인테리어 진행 상태를 서버에 저장합니다.

### 요청
```json
{
  "buildTrack": "furniture",
  "furnitureStage": 2,
  "furnitureExp": 150
}
```

---

# 3. 아파트 API (6개) ✅

## 3-1. GET /api/apartments
**아파트 목록 조회**

## 3-2. GET /api/apartments/{aptSeq}
**아파트 상세 조회**

## 3-3. POST /api/apartments/favorites
**관심 아파트 등록**

## 3-4. GET /api/apartments/favorites
**관심 아파트 목록 조회**

## 3-5. DELETE /api/apartments/favorites/{id}
**관심 아파트 삭제**

## 3-6. GET /api/apartments/regions/{regionName}/coordinates
**지역 좌표 조회**

지역명(예: "강남구")으로 해당 지역의 중심 좌표(아파트 평균 위치)를 조회합니다.

---

# 4. DSR 시뮬레이션 API (1개) ✅

## 4-1. POST /api/simulation/dsr
**DSR PRO 모드 시뮬레이션**

---

# 5. AI 매니저 API (4개) ✅

## 5-1. POST /api/ai-manager/analyze
**지출 분석 (수동/이미지)**

- `Content-Type`: `application/json` (수동) 또는 `multipart/form-data` (이미지)

### 요청 (JSON)
```json
{
  "inputMode": "MANUAL",
  "amount": 31000,
  "storeName": "치킨플러스",
  "category": "FOOD",
  "paymentDate": "2025-12-04",
  "memo": "야식"
}
```

## 5-2. POST /api/ai-manager/confirm
**추출 데이터 확인 (이미지 모드 후속)**

이미지 분석(EXTRACTING 상태) 후, 사용자가 확인/수정한 데이터를 확정하여 분석을 진행합니다.

### 요청
```json
{
  "conversationId": 123,
  "amount": 31000,
  "storeName": "치킨플러스",
  "category": "FOOD",
  "paymentDate": "2025-12-04"
}
```

## 5-3. POST /api/ai-manager/judgment
**최종 판결**

### 요청
```json
{
  "conversationId": 123,
  "selectedExcuseId": "STRESS",
  "customExcuse": ""
}
```

## 5-4. GET /api/ai-manager/history
**분석 내역 조회**

---

# 6. 드림홈 API (2개) ✅

## 6-1. POST /api/dream-home
**드림홈 설정**

### 요청
```json
{
  "aptSeq": "11410-61",
  "targetAmount": 300000000,
  "targetDate": "2028-12-31",
  "monthlyGoal": 2500000,
  "themeId": 1
}
```

## 6-2. POST /api/dream-home/savings
**저축 기록**

### 요청
```json
{
  "amount": 1000000,
  "saveType": "DEPOSIT",
  "memo": "12월 월급 저축"
}
```

---

# 7. 컬렉션 API (4개) ✅

## 7-1. GET /api/collections
**완성된 집 목록 조회**

## 7-2. GET /api/collections/{id}/journey
**저축 여정 상세 조회**

완성된 집의 Phase별 저축 기록 및 이벤트를 조회합니다.

## 7-3. GET /api/collections/in-progress/journey
**진행 중인 드림홈 여정 조회**

현재 짓고 있는 집의 여정을 조회합니다.

## 7-4. PUT /api/collections/{id}/main-display
**대표 컬렉션 설정**

---

# 8. 대시보드 API (1개) ✅

## 8-1. GET /api/users/dashboard
**대시보드 통합 데이터 조회**

---

# 9. 스트릭 API (3개) ✅

## 9-1. GET /api/streak/reward
**수령 가능한 마일스톤 조회**

## 9-2. POST /api/streak/reward
**마일스톤 보상 수령**

### 요청
```json
{
  "milestoneDays": 7
}
```

## 9-3. GET /api/streak/milestones
**전체 마일스톤 상태 조회**

---

# 10. 테마 API (1개) ✅

## 10-1. GET /api/themes
**활성 테마 목록 조회**

드림홈 설정 시 선택 가능한 하우스 테마 목록을 반환합니다.