# 🏠 Grome (Grow My Home) - Backend API

> 2030세대를 위한 현실적인 내집마련 지원 플랫폼

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.5-red.svg)](https://mybatis.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📋 목차

- [프로젝트 소개](#-프로젝트-소개)
- [핵심 컨셉](#-핵심-컨셉)
- [구현 현황](#-구현-현황)
- [기술 스택](#-기술-스택)
- [시작하기](#-시작하기)
- [API 문서](#-api-문서)
- [프로젝트 구조](#-프로젝트-구조)
- [개발 로드맵](#-개발-로드맵)

---

## 🎯 프로젝트 소개

**집중(JipJung)**은 2030세대를 위한 현실적인 내집마련 지원 플랫폼입니다.

단순 정보 제공을 넘어 **구체적인 행동 유도**와 **지속적인 동기 부여**를 제공하여,
지루한 저축 과정을 게임처럼 즐거운 경험으로 전환합니다.

### 🎮 핵심 컨셉

**저축 활동을 '집 키우기'로 게이미피케이션**

- 저축할 때마다 경험치 획득 → 레벨업
- 나만의 집과 인테리어를 꾸며가며 목표 달성

### 💡 차별점

1. **2025년 스트레스 DSR 3단계 규제 완전 반영**
   → 현실적 대출 한도 계산

2. **구매력 기반 맞춤 매물 추천**
   → 모든 매물이 아닌, 실제 살 수 있는 매물만 표시

3. **저축과 연동된 게이미피케이션 시스템**
   → 지속적 동기 부여로 목표 달성률 향상

### 🎯 타겟 사용자

- **연령**: 20대 후반 ~ 30대 초반 직장인
- **연소득**: 3,000만원 ~ 6,000만원
- **목표**: 수도권 중소형 아파트 구매 (3억 ~ 5억)

---

## ✨ 구현 현황

### ✅ 현재 구현 완료 (MVP Phase 1 - 진행중)

| 기능 | 상태 | 설명 |
|------|------|------|
| 회원가입/로그인 | ✅ 완료 | JWT 기반 인증, 이메일 중복 체크, 비밀번호 암호화 |
| 아파트 실거래가 조회 | ✅ 완료 | 검색, 필터링, 페이징 지원 |
| 관심 아파트 관리 | ✅ 완료 | 등록, 조회, 삭제, 중복 방지 |
| Swagger API 문서화 | ✅ 완료 | 자동 생성 API 문서 |
| H2/MySQL 프로파일 | ✅ 완료 | 개발/운영 환경 분리 |

### 🔄 개발 예정 (MVP Phase 1 - 우선순위 순)

| 기능 | 우선순위 | 설명                        |
|------|---------|---------------------------|
| 온보딩 프로세스 | P0 | 연소득, 기존 대출, 희망 지역 입력      |
| DSR 계산기 | P0 | 2025년 스트레스 DSR 3단계 규제 반영  |
| 드림홈 설정 | P0 | 목표 매물 설정 및 저축 목표 자동 계산    |
| 저축 기록 관리 | P0 | 저축 입력, 통계 조회              |
| 레벨 시스템 | P0 | 경험치 획득, 레벨업               |
| 집 수집 시스템 | P0 | 집 -> 인테리어 순으로 구성          |
| 구매력 기반 매물 필터링 | P0 | DSR 계산 결과 기반 구매 가능 매물만 표시 |

### 📅 Phase 2 이후 계획

- 소셜 로그인 (카카오, Google)
- 실거래 가격 추이 차트
- 대출 상환 계획 시뮬레이션
- 주간 리포트
- PWA 지원

---

## 🛠 기술 스택

### Backend Framework

```
Spring Boot 3.2.5
├── Spring Web          # RESTful API
├── Spring Security     # JWT 인증/인가
├── Spring Validation   # 입력 검증
└── MyBatis 3.0.5       # SQL 매핑
```

### Security & Authentication

- **JWT (java-jwt 4.3.0)**: Token 기반 인증
- **BCrypt**: 비밀번호 암호화 (salt rounds: 10)
- **Access Token**: 7일 유효

### Database

- **H2 Database**: 개발/테스트 (메모리 DB, 자동 초기화)
- **MySQL 8.0+**: 운영 환경

### API Documentation

- **Swagger/OpenAPI 3.0**: 자동 생성 API 문서
- **springdoc-openapi 2.2.0**

### Build & Deploy

- **Maven**: 의존성 관리
- **Java 17**: LTS 버전

---

## 🚀 시작하기

### 사전 요구사항

- ☕ Java 17 이상
- 🔨 Maven 3.6 이상
- 🐬 (선택) MySQL 8.0 이상

### 1️⃣ 프로젝트 클론

```bash
git clone https://github.com/your-repo/SSAFYHome_Framework.git
cd SSAFYHome_Framework
```

### 2️⃣ 프로파일 선택

#### 🧪 H2 데이터베이스 (기본, 개발/테스트용)

```properties
# application.properties
spring.profiles.active=h2
```

**특징:**
- 메모리 DB (재시작 시 초기화)
- 15건의 샘플 아파트 데이터 자동 로드
- H2 Console 접속 가능

#### 🐬 MySQL 데이터베이스 (운영용)

```properties
# application.properties
spring.profiles.active=mysql

# application-mysql.properties 설정 필요
spring.datasource.url=jdbc:mysql://localhost:3306/jipjung
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 3️⃣ 애플리케이션 실행

```bash
# Maven으로 실행
./mvnw spring-boot:run

# 또는 IDE에서 JipJungApplication.java 실행
```

### 4️⃣ 접속 확인

| 서비스 | URL | 설명 |
|--------|-----|------|
| **API 서버** | http://localhost:8080 | REST API |
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html | API 문서 |
| **H2 Console** | http://localhost:8080/h2-console | DB 확인 (H2 프로파일) |

**H2 Console 접속 정보:**
- JDBC URL: `jdbc:h2:mem:jipjung`
- Username: `sa`
- Password: (비어있음)

---

## 📚 API 문서

### Swagger UI 화면

애플리케이션 실행 후 http://localhost:8080/swagger-ui/index.html 접속

![Swagger API](src/main/resources/swagger-api.png)

### 주요 엔드포인트

#### 🔐 인증 API (`/api/auth`)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/auth/signup` | 회원가입 | ❌ |
| POST | `/api/auth/login` | 로그인 (JWT 발급) | ❌ |

**회원가입 요청 예시:**
```json
{
  "email": "user@example.com",
  "nickname": "홍길동",
  "password": "Test1234!@"
}
```

**로그인 응답 (Header):**
```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

#### 🏢 아파트 API (`/api/apartments`)

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | `/api/apartments` | 실거래가 목록 조회 | ❌ |
| GET | `/api/apartments/{id}` | 실거래가 상세 조회 | ❌ |
| POST | `/api/apartments/favorites` | 관심 아파트 등록 | ✅ |
| GET | `/api/apartments/favorites` | 내 관심 목록 조회 | ✅ |
| DELETE | `/api/apartments/favorites/{id}` | 관심 아파트 삭제 | ✅ |

**검색 쿼리 파라미터:**
- `legalDong`: 법정동 검색 (예: "강남구")
- `apartmentName`: 아파트명 검색 (예: "래미안")
- `dealDateFrom`, `dealDateTo`: 거래일 범위
- `minDealAmount`, `maxDealAmount`: 거래금액 범위 (만원)
- `page`, `size`: 페이징 (기본값: 0, 10)

### API 응답 형식

모든 API는 표준화된 응답 래퍼를 사용합니다:

```json
{
  "code": 200,
  "status": "OK",
  "message": "성공",
  "data": {
    // 실제 응답 데이터
  }
}
```

---

## 📂 프로젝트 구조

```
src/main/java/com/jipjung/project/
├── config/
│   ├── exception/              # 전역 예외 처리
│   │   ├── ApiResponse.java    # 표준 API 응답 (Record)
│   │   ├── ErrorCode.java      # 에러 코드 정의
│   │   └── GlobalExceptionHandler.java
│   ├── jwt/                    # JWT 인증
│   │   ├── JwtProvider.java    # 토큰 생성/검증
│   │   ├── filter/             # 인증 필터
│   │   └── handler/            # 로그인 성공/실패 핸들러
│   ├── SecurityConfig.java     # Spring Security 설정
│   └── SwaggerConfig.java      # Swagger 설정
├── controller/                 # REST API 컨트롤러
│   ├── request/                # 요청 DTO (Record)
│   ├── response/               # 응답 DTO (Record)
│   ├── AuthController.java     # 인증 API
│   └── ApartmentController.java # 아파트 API
├── domain/                     # 도메인 모델
│   ├── User.java               # 사용자
│   ├── UserRole.java           # 권한 (USER, ADMIN, GUEST)
│   ├── Apartment.java          # 아파트 기본정보
│   ├── ApartmentDeal.java      # 실거래가
│   └── FavoriteApartment.java  # 관심 아파트
├── repository/                 # 데이터 접근 (MyBatis)
│   ├── UserMapper.java
│   ├── ApartmentMapper.java
│   └── FavoriteApartmentMapper.java
└── service/                    # 비즈니스 로직
    ├── AuthService.java
    ├── ApartmentService.java
    ├── LoginService.java
    └── CustomUserDetails.java

src/main/resources/
├── mapper/                     # MyBatis XML
│   ├── UserMapper.xml
│   ├── ApartmentMapper.xml
│   └── FavoriteApartmentMapper.xml
├── application.properties      # 공통 설정
├── application-h2.properties   # H2 프로파일
├── application-mysql.properties # MySQL 프로파일
└── schema.sql                  # DB 스키마 + 샘플 데이터
```

---

## 🗄 데이터베이스

### ERD (현재 구현된 테이블)

```
┌─────────────┐       ┌──────────────────────┐
│    user     │       │ apartment_transaction│
├─────────────┤       ├──────────────────────┤
│ id (PK)     │       │ id (PK)              │
│ email       │       │ apartment_name       │
│ nickname    │       │ legal_dong           │
│ password    │       │ road_address         │
│ role        │       │ build_year           │
│ created_at  │       │ deal_amount          │
│ updated_at  │       │ deal_date            │
└─────────────┘       │ exclusive_area       │
       │              │ floor                │
       │              │ created_at           │
       │              └──────────────────────┘
       │                       │
       │                       │
       └───────┐       ┌───────┘
               │       │
        ┌──────┴───────┴──────┐
        │  favorite_apartment │
        ├─────────────────────┤
        │ id (PK)             │
        │ user_id (FK)        │
        │ apartment_trans...  │
        │ created_at          │
        └─────────────────────┘
```
---

## 📝 개발 로드맵

### ✅ Phase 1: MVP 핵심 기능 (현재)

**완료:**
- [x] 회원가입/로그인 (JWT 인증)
- [x] 아파트 실거래가 조회
- [x] 관심 아파트 관리
- [x] Swagger API 문서화
- [x] H2/MySQL 프로파일 분리

**진행 예정:**
- [ ] 온보딩 프로세스 (연소득, 희망 지역 입력)
- [ ] DSR 계산기 (스트레스 DSR 3단계 규제)
- [ ] 드림홈 설정
- [ ] 저축 기록 관리
- [ ] 레벨 시스템
- [ ] 집 수집 (4계절 × 2레벨 = 8종)
- [ ] 구매력 기반 매물 필터링

### 🔄 Phase 2: 차별화 기능

- [ ] 소셜 로그인 (카카오, Google OAuth)
- [ ] 실거래 가격 추이 차트
- [ ] 대출 상환 계획 시뮬레이션
- [ ] 드림홈 변경 이력 관리
- [ ] 연속 저축 스트릭
- [ ] 집 숲 WebP 애니메이션

### 🚀 Phase 3: 고도화

- [ ] 주간 리포트 (이메일 발송)
- [ ] 특판 상품 안내 (은행 청년 적금)
- [ ] 유사 사용자 분석
- [ ] Redis 캐싱
- [ ] PWA 변환
- [ ] 국토교통부 실거래가 API 연동

---


## 🎨 설계 원칙

### Architecture

- **Layered Architecture**: Controller → Service → Repository
- **DTO Pattern**: Record 타입 사용으로 불변성 보장
- **RESTful API**: 표준 HTTP 메서드 및 상태 코드 사용

### Code Quality

- **Java Record**: DTO를 모두 Record로 구현 (불변, 간결)
- **Validation**: Bean Validation (@Valid) 사용
- **Exception Handling**: GlobalExceptionHandler로 중앙 집중식 처리
- **API Response**: 표준화된 ApiResponse 래퍼

### Security

- **JWT**: Stateless 인증
- **BCrypt**: 비밀번호 암호화
- **CORS**: 설정된 오리진만 허용
- **SQL Injection 방지**: MyBatis 파라미터 바인딩

---

## 📝 Git Convention

### Commit Message Convention

| Type | Description |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드 |
| `chore` | 빌드 업무 수정 |

**예시:**
```
feat: Add DSR calculation API
fix: Fix duplicate favorite registration bug
docs: Update API documentation
refactor: Convert DTOs to Java Records
```

---

## 📄 라이선스

This project is licensed under the MIT License.

---

## 👥 팀원

- **SSAFY 14기** - Backend 개발

---

## 🔗 관련 문서

- [요구사항 명세서](src/main/resources/requirements.md)
- [Swagger API 문서](http://localhost:8080/swagger-ui/index.html)

---

## 📞 문의

프로젝트 관련 문의사항은 Issue 또는 Pull Request를 통해 남겨주세요.

**Built with ❤️ by SSAFY Team**
