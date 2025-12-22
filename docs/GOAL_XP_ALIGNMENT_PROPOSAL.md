# 목표 기반 XP / 11단계 정렬 제안 (결정안)

## 요약
- 저축 XP는 1만원당 1 XP 고정.
- 총 XP = 저축 XP + AI/스트릭 XP.
- 11단계(여정) 진행은 목표 XP 대비 총 XP 비율로 계산.
- 결과적으로 11단계는 "총 XP"에 의해 올라가며 저축액 전용이 아님.

본 문서는 사용자 목표 금액과 XP/단계 간 정렬 규칙을 확정하고,
관련 변경점을 요약한다.

## 결정(현재 방향)
- 1 XP = 10,000원 고정.
- 총 목표 XP = ceil(목표금액 / 10,000).
- 11단계 = floor(총XP / 목표XP * 11) + 1 (1~11 클램프).
- AI/스트릭 XP는 총 XP에 포함되어 단계 진행에 영향.

## 현황(코드 위치)
- 저축 XP 계산(고정 단위):
  - `jipjung-backend/src/main/java/com/jipjung/project/service/DreamHomeService.java`
    - EXP_PER_UNIT = 10_000
    - MAX_EXP_PER_SAVINGS = 500
- 프론트 예상 XP(고정 단위):
  - `jipjung-frontend/src/constants/exp.js`
    - UNIT_AMOUNT = 10_000
- 레벨 임계값(집 단계):
  - `jipjung-backend/src/main/java/com/jipjung/project/service/LevelPolicy.java`
  - `jipjung-frontend/src/constants/user.js`
  - 누적 기준: 0, 100, 300, 600, 1000, 1500
- growth_level.required_exp(응답 maxExp):
  - `jipjung-backend/src/main/resources/data-h2.sql`
  - 값: 100, 200, 300, 400, 500, 600
  - 참고: 최대 레벨(6)에도 required_exp가 존재
- 가구 XP 임계값(프론트만):
  - `jipjung-frontend/src/stores/gamificationStore.js`
  - 180, 205, 230, 255, 280
- 11단계 진행 계산:
  - `jipjung-backend/src/main/java/com/jipjung/project/service/CollectionService.java`
  - phase = floor(saved/target * 11) + 1

## 변경 요약(예정)
- 11단계 계산 기준을 "저축액 비율"에서 "총 XP 비율"로 변경.
- 목표 XP(분모)는 목표금액 기준으로 동적 산출.
- AI/스트릭 XP가 11단계 진행에 영향을 주도록 포함.

## 고려 사항
- 저축 없이도 AI/스트릭 XP로 단계가 올라갈 수 있음.
- "11단계 = 총 XP 진행도"라는 설명 문구가 필요함.
- max level required_exp 표기 일관성 점검 필요.

## 옵션(향후 확장 시)

### 옵션 1: 최소 변경(목표 기반 XP 단위)
- 목표 금액에 따라 "n만원 = 1 XP"를 계산해 저축 XP만 조정
- 레벨/가구 XP 구조는 유지

### 옵션 2: 11단계 전용 XP 트랙
- 11단계와 직접 매칭되는 별도의 goal XP/goal phase 도입
- 기존 레벨 XP는 유지

### 옵션 3: 11레벨 완전 통합
- 6레벨 시스템을 11레벨로 확장하여 11단계와 일치
- DB/프론트 전반 변경 필요

## 권장(현재)
- 위 결정안을 기준으로 11단계와 XP를 완전히 통합한다.

## 열린 질문
- MAX_EXP_PER_SAVINGS(500)를 유지할지, 목표 금액 규모에 따라 조정할지?
- max level(required_exp) 표기를 0 처리할지, UI에서 숨길지?
