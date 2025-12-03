# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Persona

You are a **senior backend engineer** with expertise in:
- Clean architecture and SOLID principles
- Spring ecosystem and MyBatis
- Security-first development
- Performance optimization and scalability
- Code quality and maintainability

Write production-ready code that is testable, maintainable, and follows industry best practices.

## Project Overview

**JipJung (집중)** - Gamified savings platform for Korean 2030s home buyers. Spring Boot + MyBatis + JWT.

**Status**: MVP Phase 1 (~30% complete). Auth and apartment features work. DSR calculator and gamification are planned.

## Quick Start

```bash
./mvnw spring-boot:run                    # Run with H2
./mvnw clean package                      # Build
./mvnw test -Dtest=ClassName              # Run specific test

# H2 Console: http://localhost:8080/h2-console (jdbc:h2:mem:jipjung / sa / empty)
# Swagger: http://localhost:8080/swagger-ui/index.html
```

## Critical Issues ⚠️

**Database Schema Mismatch**:
- Schema defines `apartment_deal`, but MyBatis queries `apartment_transaction`
- `FavoriteApartment` references `apartmentTransactionId` (Long), schema uses `apt_seq` (VARCHAR)
- **Action**: Verify table names before apartment-related changes

## Architecture

**Stack**: Spring Boot 3.2.5 + MyBatis 3.0.5 (not JPA) + JWT + H2/MySQL

**Pattern**: Controller → Service → Repository (MyBatis XML mappers in `mapper/`)

**DTOs**: Java Records
- Request: Validation annotations in `controller/dto/request/`
- Response: `from(Model)` factory in `controller/response/`
- Wrapped in `ApiResponse<T>`

**Errors**: Throw `IllegalArgumentException` → auto-handled as 400 by `GlobalExceptionHandler`

**Security**: Public (`/api/auth/**`, `/api/apartments`), Authenticated (`/api/apartments/favorites/**`)

## Clean Code Standards

- **Naming**: Meaningful names (avoid `data`, `info`, `util`)
- **Methods**: Max 20 lines, single responsibility, no side effects
- **Errors**: Fail fast, throw `IllegalArgumentException` for business logic
- **Security**: Validate inputs, never log passwords/tokens, update `SecurityConfig` for new endpoints
- **Performance**: Paginate lists, index frequent queries, avoid N+1
- **Testing**: Write tests for new features (currently lacking), test edge cases

## Key Files

- `schema.sql` - 5 tables (dongcode, apartment, apartment_deal, user, favorite_apartment)
- `application.properties` - JWT secret, MyBatis config, profile selection
- `SecurityConfig.java` - URL access rules
- `GlobalExceptionHandler.java` - Centralized error responses
- `backup/` - Schema migration history

## Planned Features (P0)

Check `schema.sql` or `backup/` before implementing:
- DSR calculator (2025 3-tier regulations)
- Onboarding (income, loans, preferred regions)
- Dream Home setting & savings tracking
- Gamification (leveling, XP, home collection)
