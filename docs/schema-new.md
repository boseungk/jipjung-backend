📋 [Context] 프로젝트: 집-중 (Zip-Jung) DB 설계 배경
1. 프로젝트 개요 (Identity)
   서비스명: 집-중 (Zip-Jung)

핵심 컨셉: "내 집 마련의 꿈을 시각화하는 핀테크 게이미피케이션"

서비스 목표: 사용자가 **지출을 통제(절약)**하고 저축을 늘려(목표 달성), 가상의 집을 단계별로 건축하고 수집하는 경험을 제공.

핵심 페르소나 (AI 레제): 사용자의 지출 내역을 심판하는 AI 관리인. (합리적 소비 시 칭찬, 낭비 시 독설).

2. 데이터베이스 설계의 핵심 철학 (Design Principles)
   리뷰어가 가장 먼저 이해해야 할 4가지 설계 원칙입니다.

금융 데이터와 게임 데이터의 분리 (Separation of Concerns)

원칙: 돈이 오가는 Fact(지출/저축)와 이를 해석하는 Game Logic(심판/경험치)을 물리적으로 다른 테이블로 분리한다.

이유: 게임 밸런스 패치(경험치 로직 변경 등)가 있더라도, 사용자의 실제 금전 기록(가계부)은 절대 훼손되지 않아야 함.

데이터 무결성 및 추적성 (Audit & Integrity)

원칙: 금융 관련 데이터(dream_home, savings_history, expense)는 함부로 삭제(DELETE)하지 않고, Soft Delete(is_deleted)를 적용한다.

이유: 사용자가 "실수였어요"라고 복구를 요청하거나, 과거 자산 흐름 통계를 낼 때 데이터가 보존되어야 함.

확장 가능한 테마 시스템 (Scalability)

원칙: 지금은 '모던 아파트'만 짓지만, 나중에 '한옥', '캐슬' 등 다양한 스킨이 추가될 것을 대비해 **성장 규칙(growth_level)**과 **디자인 에셋(theme_asset)**을 분리한다.

읽기 성능 최적화 (Read Optimization)

원칙: 대시보드 진입 시 쿼리 부하를 줄이기 위해, User 테이블에 자주 조회되는 집계 데이터(current_level, current_exp)를 역정규화(De-normalization)하여 저장한다.

3. 도메인별 상세 설계 배경 (Domain Context)
   A. 사용자 및 성장 (User & Growth)
   배경: 유저는 단순히 돈만 모으는 게 아니라, **RPG 게임처럼 레벨업(집 짓기)**을 합니다.

주요 테이블:

user: 기본적인 회원 정보 외에 current_level, current_exp, streak_count(연속 성공)를 포함합니다. 매번 계산하지 않고 바로 보여주기 위함입니다.

growth_level: "경험치 1000이면 레벨 2가 된다"는 게임의 룰입니다.

house_theme: "모던 스타일", "한옥 스타일" 등 집의 종류입니다.

theme_asset: (룰 + 테마) 조합에 따른 실제 이미지 URL입니다. "모던 스타일의 2레벨 이미지는 이것이다"를 정의합니다.

B. 목표 및 저축 관리 (Dream Home)
배경: 사용자는 특정 아파트를 목표(dream_home)로 설정하고 돈을 모읍니다. 단순 목표 설정이 아니라, 과거 자산 그래프를 그려줘야 합니다.

주요 테이블:

dream_home: 유저가 찜한 아파트와 목표 금액. 현재 모은 돈의 **총합(current_saved_amount)**을 가집니다.

savings_history: **"언제, 얼마를 저축했는지"**에 대한 이력입니다. (중요) 이 테이블이 있어야 대시보드에서 **"지난달 대비 자산 추이 그래프(Bento Board Chart)"**를 그릴 수 있습니다.

C. AI 매니저 및 지출 (AI Manager & Expense)
배경 (Game Loop): 유저가 영수증을 올리면 -> AI가 심판(합리/낭비)하고 -> 결과에 따라 경험치를 줍니다.

주요 테이블:

expense: 가계부 그 자체. (날짜, 금액, 상호명). 게임과 무관한 팩트입니다.

expense_review: AI의 해석입니다. (합리 여부, 유저의 변명, AI의 코멘트, 획득한 경험치).

검토 포인트: expense 테이블과 expense_review 테이블은 1:1 관계입니다. 하나의 지출에 대해 한 번의 심판만 가능합니다.

D. 컬렉션 (Collection)
배경: 집을 다 지으면(만렙 달성), 그 집은 '명예의 전당'에 저장되고 새로운 집을 지을 수 있습니다.

주요 테이블:

user_collection: 유저가 과거에 완공했던 테마들의 목록입니다. "내가 2024년에 모던 아파트를 완공했음"을 기록합니다.

E. 부동산 정보 (Real Estate - Legacy)
배경: 기존 SSAFY 프로젝트에서 사용하던 공공데이터 기반 부동산 정보입니다.

주요 테이블:

apartment, apartment_deal, dongcode: 실거래가 및 매물 정보. dream_home 테이블이 이 apartment의 ID(apt_seq)를 참조합니다.

4. 리뷰어가 집중해서 봐야 할 포인트 (Review Checklist)
   이 테이블을 검토해주시는 분은 다음 사항을 중점적으로 봐주세요.

theme_asset 구조: 테마(theme_id)와 레벨(level)을 조합해서 이미지를 찾는 방식이 효율적인가?

level PK 사용: growth_level 테이블에서 대리키(id) 대신 자연키(level)를 PK로 쓴 것이 적절한가? (현재는 직관성을 위해 자연키 채택)

인덱스 설계:

savings_history에서 (dream_home_id, created_at) 인덱스가 차트 조회 성능을 충분히 보장하는가?

expense에서 (user_id, payment_date) 인덱스가 월별 가계부 조회에 적절한가?

Soft Delete: 금융 데이터 테이블(expense, savings_history)에 is_deleted 컬럼이 모두 포함되어 있는가?

이외에도 테이블에 필요한 인덱스가 충분히 있는지 검토해주세요

또한 테이블에서 추가로 고려하거나 검토할만한 사항이 있는지 리뷰해주세요

🚀 데이터 흐름 예시 (Workflow)
상황: 사용자가 3만 원짜리 치킨 영수증을 등록하고, AI가 이를 '낭비'로 판정한 경우

Insert expense: 30,000원, '치킨플러스', 카테고리 'FOOD' 저장.

Insert expense_review: expense_id 참조, 결과 'WASTE', 경험치 변동 '-30', AI 코멘트 "살찐다" 저장.

Update user: current_exp를 -30 차감.

Check growth_level: 현재 경험치가 레벨 다운/업 구간인지 확인 (변동 없음).

Select theme_asset: 현재 레벨(유지됨)에 맞는 집 이미지를 가져와 메인 화면에 표시.