-- ============================================================================
-- JipJung Database Schema v2
-- ============================================================================
-- Description: SSAFY_HOME 기반 데이터베이스 스키마
-- Version: 2.0
-- Created: 2025-11-28
-- ============================================================================

-- ============================================================================
-- 1. Database Setup
-- ============================================================================

-- Character Set 설정 (MySQL only - H2에서는 주석 처리)
-- SET NAMES utf8mb4;
-- SET CHARACTER_SET_CLIENT = utf8mb4;
-- SET CHARACTER_SET_CONNECTION = utf8mb4;
-- SET CHARACTER_SET_RESULTS = utf8mb4;

-- USE jipjung;
-- SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
-- SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
-- SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- ============================================================================
-- 2. Drop Tables (in reverse dependency order)
-- ============================================================================

DROP TABLE IF EXISTS favorite_apartment;
DROP TABLE IF EXISTS user_preferred_area;
DROP TABLE IF EXISTS apartment_deal;
DROP TABLE IF EXISTS apartment;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS dongcode;

-- ============================================================================
-- 3. Create Tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1 dongcode - 법정동코드 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE dongcode (
    dong_code VARCHAR(10) PRIMARY KEY COMMENT '법정동코드',
    sido_name VARCHAR(30) NOT NULL COMMENT '시도명',
    gugun_name VARCHAR(30) NOT NULL COMMENT '구군명',
    dong_name VARCHAR(30) COMMENT '동명',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_sido_gugun (sido_name, gugun_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='법정동코드 테이블';

-- ----------------------------------------------------------------------------
-- 3.2 apartment - 아파트 기본정보 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE apartment (
    apt_seq VARCHAR(20) PRIMARY KEY COMMENT '아파트코드 (SSAFY 원본)',
    dong_code VARCHAR(10) COMMENT '법정동코드',
    sgg_cd VARCHAR(5) COMMENT '시군구코드',
    umd_cd VARCHAR(5) COMMENT '읍면동코드',
    umd_nm VARCHAR(20) COMMENT '읍면동명',
    jibun VARCHAR(10) COMMENT '지번',
    road_nm_sgg_cd VARCHAR(5) COMMENT '도로명시군구코드',
    road_nm VARCHAR(20) COMMENT '도로명',
    road_nm_bonbun VARCHAR(10) COMMENT '도로명번호(본번)',
    road_nm_bubun VARCHAR(10) COMMENT '도로명번호(부번)',
    apt_nm VARCHAR(40) NOT NULL COMMENT '아파트명',
    build_year INT COMMENT '건축년도',
    latitude DECIMAL(16, 13) COMMENT '위도',
    longitude DECIMAL(16, 13) COMMENT '경도',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (dong_code) REFERENCES dongcode(dong_code) ON DELETE SET NULL,
    INDEX idx_apt_nm (apt_nm),
    INDEX idx_dong_code (dong_code),
    INDEX idx_location (latitude, longitude),
    INDEX idx_build_year (build_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='아파트 기본정보 테이블';

-- ----------------------------------------------------------------------------
-- 3.3 apartment_deal - 아파트 실거래 정보 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE apartment_deal (
    deal_no BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '거래번호',
    apt_seq VARCHAR(20) NOT NULL COMMENT '아파트코드',
    apt_dong VARCHAR(40) COMMENT '동',
    floor VARCHAR(3) COMMENT '층',
    deal_year INT NOT NULL COMMENT '거래년도',
    deal_month INT NOT NULL COMMENT '거래월',
    deal_day INT NOT NULL COMMENT '거래일',
    deal_date DATE GENERATED ALWAYS AS (
        PARSEDATETIME(
            CONCAT(
                CAST(deal_year AS VARCHAR), '-',
                LPAD(CAST(deal_month AS VARCHAR), 2, '0'), '-',
                LPAD(CAST(deal_day AS VARCHAR), 2, '0')
            ),
            'yyyy-MM-dd'
        )
    ) COMMENT '거래일자 (생성컬럼)',
    exclu_use_ar DECIMAL(7,2) NOT NULL COMMENT '전용면적(㎡)',
    deal_amount VARCHAR(10) NOT NULL COMMENT '거래금액(만원)',
    deal_amount_num BIGINT GENERATED ALWAYS AS (
        CAST(REGEXP_REPLACE(deal_amount, ',', '') AS BIGINT)
    ) COMMENT '거래금액(숫자)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
    INDEX idx_deal_date (deal_date),
    INDEX idx_deal_amount (deal_amount_num),
    INDEX idx_exclu_use_ar (exclu_use_ar)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='아파트 실거래 정보 테이블';

-- ----------------------------------------------------------------------------
-- 3.4 user - 사용자 테이블 (OAuth 지원)
-- ----------------------------------------------------------------------------
CREATE TABLE `user` (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 ID',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT '이메일',
    password VARCHAR(255) COMMENT '암호화된 비밀번호 (OAuth 사용자는 NULL 가능)',
    name VARCHAR(50) COMMENT '이름',
    nickname VARCHAR(50) COMMENT '닉네임',
    phone VARCHAR(20) COMMENT '전화번호',
    profile_image_url VARCHAR(255) COMMENT '프로필 이미지 URL',

    -- OAuth 관련 필드
    social_provider VARCHAR(20) COMMENT '소셜 로그인 제공자 (kakao, naver, google)',
    social_id VARCHAR(100) COMMENT '소셜 로그인 고유 ID',

    -- 권한 및 상태
    role VARCHAR(20) DEFAULT 'USER' COMMENT '권한 (USER, ADMIN)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부',

    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_social (social_provider, social_id),
    UNIQUE KEY uk_social (social_provider, social_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='사용자 테이블';

-- ----------------------------------------------------------------------------
-- 3.5 favorite_apartment - 관심 아파트 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE favorite_apartment (
    favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '관심 아파트 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    apt_seq VARCHAR(20) NOT NULL COMMENT '아파트코드',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
    UNIQUE KEY uk_user_apt (user_id, apt_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='관심 아파트 테이블';

-- ----------------------------------------------------------------------------
-- 3.6 growth_level - 성장 레벨 규칙 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE growth_level (
    level INT PRIMARY KEY COMMENT '레벨',
    step_name VARCHAR(50) NOT NULL COMMENT '단계명 (터파기, 골조...)',
    description VARCHAR(255) COMMENT '단계 설명',
    required_exp INT NOT NULL COMMENT '다음 레벨업 필요 경험치',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
) COMMENT='성장 레벨 규칙 테이블';

-- ----------------------------------------------------------------------------
-- 3.7 house_theme - 하우스 테마 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE house_theme (
    theme_id INT AUTO_INCREMENT PRIMARY KEY,
    theme_code VARCHAR(20) UNIQUE NOT NULL COMMENT 'MODERN, HANOK, CASTLE...',
    theme_name VARCHAR(50) NOT NULL COMMENT '테마 이름',
    image_path VARCHAR(100) COMMENT '상대 경로 (예: themes/modern/phase.svg)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '현재 선택 가능 여부',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
) COMMENT='하우스 테마 테이블';

-- theme_asset 테이블 삭제됨 (단일 SVG per theme 아키텍처로 전환)
-- 이제 house_theme.image_path 컬럼 사용

-- ----------------------------------------------------------------------------
-- 3.9 user 테이블 컬럼 추가 (게임/금융)
-- ----------------------------------------------------------------------------
ALTER TABLE `user` ADD COLUMN onboarding_completed BOOLEAN DEFAULT FALSE;
ALTER TABLE `user` ADD COLUMN birth_year INT;
ALTER TABLE `user` ADD COLUMN annual_income BIGINT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN existing_loan_monthly BIGINT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN current_level INT DEFAULT 1;
ALTER TABLE `user` ADD COLUMN current_exp INT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN streak_count INT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN last_streak_date DATE;
ALTER TABLE `user` ADD COLUMN max_streak INT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN selected_theme_id INT;
ALTER TABLE `user` ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

-- 인테리어(가구) 진행 상태 컬럼 (Phase 2: 집 완공 후 가구 배치)
ALTER TABLE `user` ADD COLUMN build_track VARCHAR(20) DEFAULT 'house';
ALTER TABLE `user` ADD COLUMN furniture_stage INT DEFAULT 0;
ALTER TABLE `user` ADD COLUMN furniture_exp INT DEFAULT 0;

-- ----------------------------------------------------------------------------
-- 3.10 dream_home - 드림홈(목표) 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE dream_home (
    dream_home_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    apt_seq VARCHAR(20) NOT NULL,
    house_name VARCHAR(100) COMMENT '유저가 붙인 집 이름',
    target_amount BIGINT NOT NULL COMMENT '목표 금액',
    target_date DATE NOT NULL COMMENT '목표 달성일',
    monthly_goal BIGINT COMMENT '월 목표 저축액',
    current_saved_amount BIGINT DEFAULT 0 COMMENT '현재까지 모은 금액',
    start_date DATE DEFAULT CURRENT_DATE COMMENT '목표 시작일',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE, COMPLETED, GIVEN_UP',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
    INDEX idx_dream_user_status (user_id, status)
) COMMENT='드림홈(목표) 테이블';

-- ----------------------------------------------------------------------------
-- 3.11 savings_history - 저축 내역 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE savings_history (
    savings_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dream_home_id BIGINT NOT NULL,
    amount BIGINT NOT NULL COMMENT '저축/인출 금액',
    save_type VARCHAR(20) NOT NULL COMMENT 'DEPOSIT 또는 WITHDRAW',
    memo VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (dream_home_id) REFERENCES dream_home(dream_home_id) ON DELETE CASCADE,
    INDEX idx_save_home_date (dream_home_id, is_deleted, created_at)
) COMMENT='저축 내역 테이블';

-- ----------------------------------------------------------------------------
-- 3.12 streak_history - 스트릭 기록 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE streak_history (
    streak_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    streak_date DATE NOT NULL COMMENT '참여 날짜',
    exp_earned INT DEFAULT 0 COMMENT '획득 경험치',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_date (user_id, streak_date),
    INDEX idx_streak_user_date (user_id, streak_date)
) COMMENT='스트릭 기록 테이블';

-- ----------------------------------------------------------------------------
-- 3.13 streak_milestone_reward - 스트릭 마일스톤 보상 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE streak_milestone_reward (
    reward_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    milestone_days INT NOT NULL COMMENT '마일스톤 일수 (7, 30, 100)',
    exp_reward INT NOT NULL COMMENT '지급된 경험치',
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    streak_count_at_claim INT NOT NULL COMMENT '수령 시점의 연속일수',

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_milestone (user_id, milestone_days)
) COMMENT='스트릭 마일스톤 보상 테이블';

-- ----------------------------------------------------------------------------
-- 3.14 user_collection - 완성한 집 컬렉션
-- ----------------------------------------------------------------------------
CREATE TABLE user_collection (
    collection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    theme_id INT NOT NULL,
    dream_home_id BIGINT COMMENT '원본 드림홈 ID (여정 조회용)',
    house_name VARCHAR(100) COMMENT '유저가 붙인 집 이름',
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '완공 날짜',
    is_main_display BOOLEAN DEFAULT FALSE COMMENT '대표 전시 여부',
    total_saved BIGINT COMMENT '완공까지 모은 총액',
    duration_days INT COMMENT '완공까지 걸린 일수',

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    FOREIGN KEY (theme_id) REFERENCES house_theme(theme_id) ON DELETE CASCADE,
    FOREIGN KEY (dream_home_id) REFERENCES dream_home(dream_home_id) ON DELETE SET NULL,
    UNIQUE KEY uk_dream_home (dream_home_id),
    INDEX idx_collection_user (user_id)
) COMMENT='완성한 집 컬렉션';

-- ----------------------------------------------------------------------------
-- 3.15 user_preferred_area - 선호 지역 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE user_preferred_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    area_name VARCHAR(50) NOT NULL COMMENT '선호 지역명 (강남구, 서초구 등)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
    INDEX idx_user_area (user_id)
) COMMENT='선호 지역 테이블';

-- ============================================================================
-- 4. Phase 2: DSR 상태 관리
-- ============================================================================

-- User 테이블에 DSR 관련 컬럼 추가
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS dsr_mode VARCHAR(10) DEFAULT 'LITE';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS last_dsr_calculation_at TIMESTAMP NULL;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS cached_max_loan_amount BIGINT NULL;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS current_assets BIGINT DEFAULT 0;

-- DSR 계산 이력 테이블
CREATE TABLE IF NOT EXISTS dsr_calculation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    input_json CLOB NOT NULL,
    result_json CLOB NOT NULL,
    dsr_mode VARCHAR(10) NOT NULL,
    max_loan_amount BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_dsr_history_user ON dsr_calculation_history(user_id, created_at DESC);

-- ============================================================================
-- 5. Restore Settings
-- ============================================================================

-- SET SQL_MODE=@OLD_SQL_MODE;
-- SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
-- SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;

-- ============================================================================
-- 6. Verify Tables Created
-- ============================================================================

-- SHOW TABLES;

-- ============================================================================
-- 7. AI Conversation (Phase: AI Manager)
-- ============================================================================

-- AI 대화 테이블
CREATE TABLE IF NOT EXISTS ai_conversation (
    conversation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- 영수증 정보
    amount BIGINT,
    store_name VARCHAR(100),
    category VARCHAR(30),
    payment_date DATE,
    memo VARCHAR(255),
    receipt_image_url VARCHAR(500),

    -- AI 분석/판결 결과 (JSON 저장)
    analysis_result_json JSON COMMENT 'AI 분석 결과 JSON',
    judgment_result_json JSON COMMENT 'AI 판결 결과 JSON',

    -- 변명 정보
    selected_excuse_id VARCHAR(30),
    custom_excuse VARCHAR(500),

    -- 판결 핵심값 (역정규화 - 조회 최적화)
    judgment_result VARCHAR(20),
    judgment_score INT,
    exp_change INT DEFAULT 0,

    -- 상태 관리
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ai_conv_user ON ai_conversation(user_id, created_at DESC);

-- ============================================================================
-- 8. Daily Activity (Phase: Activity-Based Streak)
-- ============================================================================

-- 일일 활동 기록 테이블
CREATE TABLE IF NOT EXISTS daily_activity (
    activity_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL COMMENT '활동 날짜 (KST)',
    activity_type VARCHAR(30) NOT NULL COMMENT 'DASHBOARD, AI_ANALYSIS, AI_JUDGMENT, SAVINGS',
    exp_earned INT DEFAULT 0 COMMENT '획득 경험치',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 복합 유니크: 같은 날 같은 활동 유형은 1회만
    CONSTRAINT uk_user_activity_date_type 
        UNIQUE (user_id, activity_date, activity_type),

    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_daily_activity_user_date ON daily_activity(user_id, activity_date);

-- ============================================================================
-- End of Schema DDL
-- ============================================================================


