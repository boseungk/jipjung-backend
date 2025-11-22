-- ============================================================================
-- SSAFYHome Database Schema DDL
-- ============================================================================
-- Description: Database schema for SSAFYHome application
-- Version: 1.0
-- Author: SSAFY Team
-- Created: 2025-11-07
-- ============================================================================

-- ============================================================================
-- 1. Database Setup
-- ============================================================================

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS ssafyhome
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ssafyhome;

-- ============================================================================
-- 2. Drop Tables (in reverse dependency order)
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS user_house_trees;
DROP TABLE IF EXISTS user_levels;
DROP TABLE IF EXISTS price_history;
DROP TABLE IF EXISTS dsr_calculations;
DROP TABLE IF EXISTS saving_records;
DROP TABLE IF EXISTS dream_homes;
DROP TABLE IF EXISTS preferred_areas;
DROP TABLE IF EXISTS house_trees;
DROP TABLE IF EXISTS policy_history;
DROP TABLE IF EXISTS properties;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 3. Core Tables - Independent Tables (No FK dependencies)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1 users - User Basic Information
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 고유 ID',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
    password VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    name VARCHAR(50) NOT NULL COMMENT '사용자 이름',
    birth_year INT NOT NULL COMMENT '출생연도 (YYYY)',
    annual_income BIGINT NOT NULL COMMENT '연소득 (원 단위)',
    existing_loan_monthly BIGINT DEFAULT 0 COMMENT '기존 대출 월 상환액 (원)',
    profile_image_url VARCHAR(255) COMMENT '프로필 이미지 URL',
    phone VARCHAR(20) COMMENT '휴대폰 번호',
    social_provider VARCHAR(20) COMMENT '소셜 로그인 제공자 (kakao, naver, google)',
    social_id VARCHAR(100) COMMENT '소셜 로그인 고유 ID',
    is_active BOOLEAN DEFAULT TRUE COMMENT '계정 활성화 여부',

    -- Onboarding & Consent
    onboarding_completed BOOLEAN DEFAULT FALSE COMMENT '온보딩 완료 여부',
    onboarding_completed_at DATETIME COMMENT '온보딩 완료 시각',
    consent_marketing BOOLEAN DEFAULT FALSE COMMENT '마케팅 수신 동의',
    region_preference_metro BOOLEAN DEFAULT NULL COMMENT '수도권 선호 (TRUE: 수도권, FALSE: 비수도권, NULL: 미설정)',

    last_login_at DATETIME COMMENT '마지막 로그인 시각',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX idx_email (email),
    INDEX idx_social_provider_id (social_provider, social_id),
    INDEX idx_birth_year (birth_year),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 테이블';

-- ----------------------------------------------------------------------------
-- 3.2 properties - Real Estate Property Information
-- ----------------------------------------------------------------------------
CREATE TABLE properties (
    property_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '매물 고유 ID',
    apt_name VARCHAR(100) NOT NULL COMMENT '아파트 이름',
    sido VARCHAR(20) NOT NULL COMMENT '시/도',
    gugun VARCHAR(30) NOT NULL COMMENT '구/군',
    dong VARCHAR(30) NOT NULL COMMENT '동/읍/면',
    jibun VARCHAR(50) COMMENT '지번',
    road_name VARCHAR(100) COMMENT '도로명 주소',
    building_year INT COMMENT '건축연도',
    exclusive_area DECIMAL(10,2) COMMENT '전용면적 (㎡)',
    floor INT COMMENT '층수',
    price BIGINT NOT NULL COMMENT '매매가 (만원)',
    trade_date DATE NOT NULL COMMENT '거래일자',
    trade_type VARCHAR(20) DEFAULT '매매' COMMENT '거래 유형 (매매/전세/월세)',

    -- Additional convenience information
    subway_station VARCHAR(50) COMMENT '최근접 지하철역',
    subway_line VARCHAR(20) COMMENT '호선 (예: 2호선)',
    subway_distance INT COMMENT '역까지 거리 (미터)',
    walking_time_to_subway INT COMMENT '지하철역까지 도보 시간 (분)',
    recent_price_trend VARCHAR(20) COMMENT '최근 가격 추세 (UP, DOWN, STABLE)',

    -- Metadata
    data_source VARCHAR(50) COMMENT '데이터 출처 (국토부/네이버/직방)',
    last_updated_at DATETIME COMMENT '최종 업데이트 시각',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX idx_location (sido, gugun, dong),
    INDEX idx_apt_name (apt_name),
    INDEX idx_price (price),
    INDEX idx_trade_date (trade_date),
    INDEX idx_exclusive_area (exclusive_area),
    INDEX idx_subway_station (subway_station),
    FULLTEXT idx_fulltext_apt (apt_name, road_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='부동산 매물 테이블';

-- ----------------------------------------------------------------------------
-- 3.3 policy_history - Financial Policy History
-- ----------------------------------------------------------------------------
CREATE TABLE policy_history (
    policy_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '정책 고유 ID',

    effective_date DATE NOT NULL COMMENT '시행일',
    dsr_limit DECIMAL(5,2) NOT NULL COMMENT 'DSR 한도 (%)',
    stress_rate_metro DECIMAL(5,2) NOT NULL COMMENT '수도권 스트레스 금리 (%)',
    stress_rate_non_metro DECIMAL(5,2) NOT NULL COMMENT '비수도권 스트레스 금리 (%)',
    stress_stage INT NOT NULL COMMENT '스트레스 DSR 단계 (1/2/3)',

    policy_name VARCHAR(100) COMMENT '정책명',
    description TEXT COMMENT '정책 설명',
    source_url VARCHAR(255) COMMENT '출처 URL (금융위 공지)',

    is_current BOOLEAN DEFAULT FALSE COMMENT '현재 적용 중인 정책',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    INDEX idx_effective_date (effective_date),
    INDEX idx_is_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금융 정책 이력 테이블';

-- ----------------------------------------------------------------------------
-- 3.4 house_trees - House Tree Master Data
-- ----------------------------------------------------------------------------
CREATE TABLE house_trees (
    tree_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '집나무 고유 ID',
    tree_code VARCHAR(50) NOT NULL UNIQUE COMMENT '집나무 코드 (예: spring_sakura_lv3)',
    tree_name VARCHAR(100) NOT NULL COMMENT '집나무 이름 (예: 봄 벚꽃지붕 집나무)',
    tree_season VARCHAR(20) NOT NULL COMMENT '계절 테마 (SPRING, SUMMER, FALL, WINTER)',
    tree_description TEXT COMMENT '집나무 설명',

    -- WebP Animation Information
    animation_url VARCHAR(255) NOT NULL COMMENT 'Animated WebP 파일 CDN URL',
    thumbnail_url VARCHAR(255) COMMENT '정적 썸네일 이미지 URL (목록용)',
    animation_duration_ms INT COMMENT '애니메이션 총 길이 (밀리초)',
    file_size_kb INT COMMENT '파일 크기 (KB)',

    -- Unlock Conditions
    unlock_condition VARCHAR(100) COMMENT '획득 조건 (예: 월 목표 달성, 레벨 5 달성)',
    required_level INT DEFAULT 1 COMMENT '필요 레벨',
    required_savings_amount BIGINT COMMENT '필요 총 저축액 (원)',

    -- Rarity
    rarity VARCHAR(20) DEFAULT 'COMMON' COMMENT '희귀도 (COMMON, RARE, EPIC, LEGENDARY)',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    INDEX idx_season_rarity (tree_season, rarity),
    INDEX idx_required_level (required_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='집나무 마스터 테이블';

-- ============================================================================
-- 4. Dependent Tables (With FK references)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 4.1 preferred_areas - User Preferred Areas
-- ----------------------------------------------------------------------------
CREATE TABLE preferred_areas (
    area_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '희망 지역 고유 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    sido VARCHAR(20) NOT NULL COMMENT '시/도 (예: 서울특별시)',
    gugun VARCHAR(30) NOT NULL COMMENT '구/군 (예: 강남구)',
    dong VARCHAR(30) COMMENT '동/읍/면 (선택, 예: 역삼동)',
    priority INT DEFAULT 1 COMMENT '우선순위 (1=최우선)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_sido_gugun (sido, gugun),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 희망 지역 테이블';

-- ----------------------------------------------------------------------------
-- 4.2 dream_homes - Dream Home Settings
-- ----------------------------------------------------------------------------
CREATE TABLE dream_homes (
    dream_home_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '드림홈 고유 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    property_id BIGINT NOT NULL COMMENT '매물 ID',

    -- Goal Settings
    target_amount BIGINT NOT NULL COMMENT '목표 금액 (원 단위, 계약금)',
    target_date DATE NOT NULL COMMENT '목표 달성 날짜',
    monthly_goal BIGINT NOT NULL COMMENT '월 저축 목표 (원)',

    -- Calculated Information
    down_payment_ratio DECIMAL(5,2) DEFAULT 30.00 COMMENT '계약금 비율 (%)',
    estimated_loan_amount BIGINT COMMENT '예상 대출 금액 (원)',
    estimated_achievement_date DATE COMMENT '현재 저축 속도 기반 예상 달성일',
    days_ahead_or_behind INT DEFAULT 0 COMMENT '목표 대비 앞당겨진(+)/늦춰진(-) 일수',

    -- Status Management
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, CHANGED, ACHIEVED, ABANDONED)',
    is_current BOOLEAN DEFAULT TRUE COMMENT '현재 드림홈 여부',

    -- History Tracking
    activated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '활성화 시각',
    deactivated_at DATETIME COMMENT '비활성화 시각',
    deactivation_reason VARCHAR(50) COMMENT '변경 사유',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '설정일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_property_id (property_id),
    INDEX idx_is_current (is_current),
    INDEX idx_target_date (target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='드림홈 목표 설정 테이블';

-- ----------------------------------------------------------------------------
-- 4.3 saving_records - Saving Records
-- ----------------------------------------------------------------------------
CREATE TABLE saving_records (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '저축 기록 고유 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',

    amount BIGINT NOT NULL COMMENT '저축 금액 (원 단위)',
    saving_method VARCHAR(50) COMMENT '저축 방법 (예: KB청년희망적금, 자유적금)',
    account_number VARCHAR(50) COMMENT '계좌번호 (선택)',
    memo TEXT COMMENT '메모',

    saved_date DATE NOT NULL COMMENT '저축한 날짜',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '기록 등록일시',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_saved_date (saved_date),
    INDEX idx_user_saved_date (user_id, saved_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='저축 기록 테이블';

-- ----------------------------------------------------------------------------
-- 4.4 dsr_calculations - DSR Calculation History
-- ----------------------------------------------------------------------------
CREATE TABLE dsr_calculations (
    calculation_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '계산 기록 고유 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',

    -- Input Values
    annual_income BIGINT NOT NULL COMMENT '연소득 (원)',
    existing_loan_monthly BIGINT NOT NULL COMMENT '기존 대출 월 상환액 (원)',
    loan_amount BIGINT NOT NULL COMMENT '신규 대출 희망액 (원)',
    interest_rate DECIMAL(5,2) NOT NULL COMMENT '실제 금리 (%)',
    stress_rate DECIMAL(5,2) NOT NULL COMMENT '스트레스 가산금리 (%)',
    loan_period_years INT NOT NULL COMMENT '대출 기간 (년)',

    -- Calculation Results
    monthly_payment BIGINT NOT NULL COMMENT '월 상환액 (원)',
    total_monthly_debt BIGINT NOT NULL COMMENT '총 월 부채 상환액 (원)',
    dsr_ratio DECIMAL(5,2) NOT NULL COMMENT 'DSR 비율 (%)',
    max_loan_amount BIGINT NOT NULL COMMENT '최대 대출 가능액 (원)',
    is_approved BOOLEAN NOT NULL COMMENT '승인 가능 여부 (DSR 40% 이하)',

    -- Policy Information
    policy_id BIGINT COMMENT '적용된 정책 ID',

    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '계산 시각',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (policy_id) REFERENCES policy_history(policy_id),
    INDEX idx_user_id (user_id),
    INDEX idx_calculated_at (calculated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DSR 계산 이력 테이블';

-- ----------------------------------------------------------------------------
-- 4.5 price_history - Property Price History
-- ----------------------------------------------------------------------------
CREATE TABLE price_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '이력 고유 ID',
    property_id BIGINT NOT NULL COMMENT '매물 ID',

    price BIGINT NOT NULL COMMENT '가격 (만원)',
    trade_date DATE NOT NULL COMMENT '거래/조사 날짜',
    trade_type VARCHAR(20) NOT NULL COMMENT '거래 유형',

    price_change_amount INT COMMENT '이전 대비 변동액 (만원)',
    price_change_ratio DECIMAL(5,2) COMMENT '이전 대비 변동률 (%)',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '기록 등록일시',

    FOREIGN KEY (property_id) REFERENCES properties(property_id) ON DELETE CASCADE,
    INDEX idx_property_id (property_id),
    INDEX idx_trade_date (trade_date),
    INDEX idx_property_trade_date (property_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='매물 가격 변동 이력 테이블';

-- ============================================================================
-- 5. Gamification Tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 5.1 user_levels - User Level Management
-- ----------------------------------------------------------------------------
CREATE TABLE user_levels (
    level_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '레벨 기록 고유 ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '사용자 ID',

    current_level INT DEFAULT 1 COMMENT '현재 레벨',
    experience_points INT DEFAULT 0 COMMENT '경험치',
    level_title VARCHAR(50) COMMENT '레벨 타이틀 (예: 꾸준한 실천가)',

    total_savings_amount BIGINT DEFAULT 0 COMMENT '총 저축 금액 (원)',
    total_saving_days INT DEFAULT 0 COMMENT '총 저축 일수',
    current_streak INT DEFAULT 0 COMMENT '현재 연속 저축 일수',
    longest_streak INT DEFAULT 0 COMMENT '최장 연속 저축 일수',

    next_level_threshold INT COMMENT '다음 레벨 필요 경험치',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '레벨 시작일시',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_current_level (current_level),
    INDEX idx_experience_points (experience_points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 레벨 테이블';

-- ----------------------------------------------------------------------------
-- 5.2 user_house_trees - User House Tree Collection
-- ----------------------------------------------------------------------------
CREATE TABLE user_house_trees (
    user_tree_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 집나무 고유 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    tree_id BIGINT NOT NULL COMMENT '집나무 ID',

    acquired_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '획득 시각',
    tree_level INT DEFAULT 1 COMMENT '집나무 레벨 (같은 집나무 중복 획득 시 레벨업)',
    is_displayed_in_forest BOOLEAN DEFAULT TRUE COMMENT '내 숲에 표시 여부',

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (tree_id) REFERENCES house_trees(tree_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_acquired_at (acquired_at),
    UNIQUE KEY uk_user_tree (user_id, tree_id) COMMENT '동일 집나무는 1개만 (레벨업 방식)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 집나무 수집 테이블';

-- ============================================================================
-- 6. Schema Creation Complete
-- ============================================================================

-- Verify tables created
SELECT
    TABLE_NAME,
    TABLE_TYPE,
    ENGINE,
    TABLE_ROWS,
    TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ssafyhome'
ORDER BY TABLE_NAME;

-- ============================================================================
-- End of Schema DDL
-- ============================================================================
