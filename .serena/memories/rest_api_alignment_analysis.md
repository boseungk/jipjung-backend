# REST API 정렬 분석 (REST API.md vs 실제 구현)

## 요약
- **문서의 총 API 수**: 24개
- **실제 구현된 API**: 7개 (인증 2개 + 아파트 5개)
- **불일치 사항 발견**: 있음 (요청 DTO 필드명, 응답 형식)

## 1. 인증 API (2개 구현)

### 1-1. POST /api/auth/signup
**문서 vs 실제 구현 비교**:

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| HTTP 메서드 | POST | POST | ✓ |
| 경로 | /api/auth/register | /api/auth/signup | **X** |
| 요청 필드 | email, password, name | email, nickname, password | **X** (name→nickname) |
| 응답 필드 | accessToken, refreshToken, user{...} | email, nickname | **X** (토큰, user 객체 없음) |
| HTTP 상태 | 201 | 201 | ✓ |

**문제점**:
- 엔드포인트: `/auth/register` vs `/auth/signup`
- 요청 필드: name vs nickname
- 응답: 토큰과 사용자 정보 부재

### 1-2. POST /api/auth/login
**문서 vs 실제 구현 비교**:

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| HTTP 메서드 | POST | POST | ✓ |
| 경로 | /api/auth/login | /api/auth/login | ✓ |
| 요청 필드 | email, password | email, password | ✓ |
| 응답 필드 | accessToken, refreshToken, user{...} | nickname | **X** |
| HTTP 상태 | 200 | 200 | ✓ |
| 인증 | 불필요 | 불필요 | ✓ |

**문제점**:
- 응답: 토큰과 사용자 정보 부재 (nickname만 반환)

## 2. 아파트 API (5개 구현)

### 2-1. GET /api/apartments 또는 GET /api/properties
**문서**: GET /api/properties (properties 사용)
**실제 구현**: GET /api/apartments (apartments 사용)

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| 경로 | /api/properties | /api/apartments | **X** |
| 쿼리 파라미터 | page, limit, sortBy, sortOrder, propertyType, ... | aptNm, umdNm, dealDateFrom, dealDateTo, minDealAmount, maxDealAmount, page, size | **X** |
| 응답 구조 | properties[], total, page, totalPages, limit | apartments[], totalCount, page, size, totalPages | **X** |

**문제점**:
- 엔드포인트: properties vs apartments
- 쿼리 파라미터 완전히 다름 (RESTful API 설계 차이)
- 응답 필드명 불일치

### 2-2. GET /api/apartments/{aptSeq} 또는 GET /api/properties/{id}
**문서**: GET /api/properties/{propertyId}
**실제 구현**: GET /api/apartments/{aptSeq}

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| 경로 | /api/properties/{propertyId} | /api/apartments/{aptSeq} | **X** |
| Path 파라미터 | propertyId | aptSeq | **X** |
| 응답 필드 | id, title, propertyType, transactionType, price, ... | aptSeq, aptNm, umdNm, jibun, roadNm, ... deals[] | **X** |

**문제점**:
- Path 파라미터 명칭 다름
- 응답 필드 완전히 다름 (문서는 부동산 매물 중심, 구현은 아파트 실거래 데이터)

### 2-3. POST /api/apartments/favorites 또는 POST /api/users/saved-properties/{id}/toggle
**문서**: POST /api/users/saved-properties/{propertyId}/toggle
**실제 구현**: POST /api/apartments/favorites

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| 경로 | /api/users/saved-properties/{id}/toggle | /api/apartments/favorites | **X** |
| 요청 방식 | Path 파라미터 | JSON Body (FavoriteRequest) | **X** |
| 요청 필드 | (Path에 ID만) | aptSeq | **X** |
| 응답 필드 | isSaved, message | id, aptSeq, aptNm, umdNm, ... | **X** |
| HTTP 상태 | 200 | 201 | **X** |

**문제점**:
- 엔드포인트 구조 완전히 다름
- 요청 방식 다름

### 2-4. GET /api/apartments/favorites 또는 GET /api/users/saved-properties
**문서**: GET /api/users/saved-properties
**실제 구현**: GET /api/apartments/favorites

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| 경로 | /api/users/saved-properties | /api/apartments/favorites | **X** |
| 응답 구조 | properties[], total, page, totalPages | List<FavoriteResponse> | **X** |
| 응답 필드 | id, title, propertyType, price, ... | id, aptSeq, aptNm, umdNm, ... | **X** |
| 인증 | 필요 | 필요 | ✓ |

**문제점**:
- 엔드포인트 경로 다름
- 응답 구조 다름

### 2-5. DELETE /api/apartments/favorites/{id} 또는 DELETE /api/users/saved-properties/{id}
**문서**: DELETE /api/users/saved-properties/{propertyId}
**실제 구현**: DELETE /api/apartments/favorites/{id}

| 항목 | 문서 | 실제 구현 | 불일치 |
|------|------|---------|--------|
| 경로 | /api/users/saved-properties/{id} | /api/apartments/favorites/{id} | **X** |
| 응답 | message | (빈 응답) | **X** |

## 3. 미구현 API (17개)

### Phase 1 (미구현):
- POST /api/auth/refresh
- GET /api/auth/me
- POST /api/auth/logout
- PUT /api/auth/onboarding (또는 PUT /users/onboarding)
- PUT /api/users/profile

### Phase 2 (미구현):
- GET /api/users/dashboard
- PUT /api/users/dream-home
- POST /api/users/dream-home/progress
- POST /api/users/gamification/experience
- POST /api/users/gamification/streak

### Phase 3 (미구현):
- GET /api/users/saved-properties/ids
- GET /api/receipts
- POST /api/receipts
- PUT /api/receipts/{receiptId}/process
- GET /api/users/statistics/monthly-spending
- GET /api/users/statistics/profile
- GET /api/users/collections

## 4. 응답 형식 분석

### ApiResponse 구조
**실제 구현**:
```json
{
  "code": 200,
  "status": "OK",
  "message": "성공",
  "data": { /* 실제 데이터 */ }
}
```

**문서에서 표시한 형식**:
```json
{
  "data": { /* 실제 데이터 */ },
  "message": "작업 완료 메시지"
}
```

**문제점**: 응답 형식 다름 (code, status 필드 추가됨)

## 5. 에러 응답 형식

**실제 구현**:
- GlobalExceptionHandler에서 ApiResponse로 통일
- ErrorCode 열거형 사용 (DUPLICATE_EMAIL: 409, USER_NOT_FOUND: 404 등)

**문서에서 표시한 형식**:
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "사용자 친화적 에러 메시지"
  }
}
```

**문제점**: 에러 응답 형식 다름

## 6. REST API 원칙 검토

### 현재 구현의 문제점:
1. **아파트 vs 매물 리소스명 혼용**:
   - 문서: /properties (부동산 매물)
   - 구현: /apartments (아파트 실거래 데이터)
   
2. **사용자 리소스 경로 다름**:
   - 문서: /users/saved-properties
   - 구현: /apartments/favorites

3. **일관되지 않은 쿼리 파라미터**:
   - 문서: RESTful 필터링 (propertyType, sido, priceMin, priceMax, ...)
   - 구현: DB 필드명 직접 사용 (aptNm, umdNm, dealDateFrom, ...)

4. **토글 vs 직접 생성**:
   - 문서: POST .../toggle (토글 개념)
   - 구현: POST /apartments/favorites (직접 생성)

## 7. 불일치 요약

### 심각한 불일치 (API 동작 방식 달라짐):
- 회원가입 엔드포인트: /auth/register vs /auth/signup
- 회원가입 요청 필드: name vs nickname
- 응답: 토큰/사용자 정보 포함 vs 최소 정보만 반환
- 아파트 목록 조회: /properties vs /apartments
- 아파트 목록 쿼리 파라미터: 완전히 다름
- 관심 아파트 저장: /users/saved-properties/{id}/toggle vs /apartments/favorites

### 중간 불일치 (응답 형식만 다름):
- 아파트 응답 필드: propertyType, title, ... vs aptSeq, aptNm, ...
- 응답 구조: 페이징 필드명 (limit vs size)

### 경미한 불일치 (기능은 동일):
- DELETE 응답: message vs 빈 응답
