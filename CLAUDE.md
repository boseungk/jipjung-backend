# CLAUDE.md

## Persona

**시니어 백엔드 엔지니어** - Spring Boot, MyBatis, JWT 전문. 클린 아키텍처와 보안 우선 개발.

## Project Overview

**JipJung (집중)** - 한국 2030 세대를 위한 감성 저축 게이미피케이션 플랫폼.

**Tech Stack**: Spring Boot 3.2.5 + MyBatis 3.0.5 + JWT + H2/MySQL + Gemini AI(Vertex AI)

**Status**: MVP 핵심 기능 완료 (인증, 대시보드, DSR, 게이미피케이션, AI 매니저, 컬렉션)

## Quick Start

```bash
./mvnw spring-boot:run                    # H2로 실행
./mvnw clean package                      # 빌드
./mvnw test -Dtest=ClassName              # 테스트

# H2 Console: localhost:8080/h2-console (jdbc:h2:mem:jipjung / sa / empty)
# Swagger: localhost:8080/swagger-ui/index.html
```

## Architecture

```
src/main/java/com/jipjung/project/
├── controller/           # REST API (10개 컨트롤러)
│   ├── AuthController         # 인증 (login, register, onboarding)
│   ├── DashboardController    # 대시보드 통합 API
│   ├── DreamHomeController    # 드림홈 설정/저축
│   ├── DsrController          # DSR 시뮬레이션
│   ├── StreakController       # 스트릭/레벨/XP
│   ├── CollectionController   # 완료된 드림홈 컬렉션
│   ├── AiManagerController    # AI 매니저 채팅
│   ├── ThemeController        # 테마 목록
│   ├── ApartmentController    # 매물 검색/즐겨찾기
│   └── UserController         # 프로필 관리
├── service/              # 비즈니스 로직
├── repository/           # MyBatis Mapper 인터페이스
├── domain/               # 엔티티
├── dsr/                  # DSR 계산 (2025 3단계 규제)
├── ai/                   # Gemini AI 통합
├── config/               # Security, JWT, Vertex AI 설정
└── global/               # 예외 처리, 유틸리티
```

**Pattern**: Controller → Service → Repository (MyBatis XML: `resources/mapper/`)

## Code Standards

- **DTOs**: Java Records (`controller/dto/`)
- **Response**: `ApiResponse<T>` 래핑
- **Errors**: `IllegalArgumentException` → `GlobalExceptionHandler`에서 400 처리
- **Security**: `/api/auth/**`, `/api/apartments`, `/api/themes` Public / 나머지 인증 필요

## Key Files

- `schema.sql` - DB 스키마
- `SecurityConfig.java` - URL 접근 규칙
- `application.properties` - JWT, MyBatis, 프로필 설정
