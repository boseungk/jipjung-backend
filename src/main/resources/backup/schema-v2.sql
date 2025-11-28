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

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- ============================================================================
-- 2. Drop Tables (in reverse dependency order)
-- ============================================================================

DROP TABLE IF EXISTS favorite_apartment;
DROP TABLE IF EXISTS apartment_deal;
DROP TABLE IF EXISTS apartment;
DROP TABLE IF EXISTS user;
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
        STR_TO_DATE(CONCAT(deal_year, '-', LPAD(deal_month, 2, '0'), '-', LPAD(deal_day, 2, '0')), '%Y-%m-%d')
    ) STORED COMMENT '거래일자 (생성컬럼)',
    exclu_use_ar DECIMAL(7,2) NOT NULL COMMENT '전용면적(㎡)',
    deal_amount VARCHAR(10) NOT NULL COMMENT '거래금액(만원)',
    deal_amount_num BIGINT GENERATED ALWAYS AS (
        CAST(REPLACE(deal_amount, ',', '') AS UNSIGNED)
    ) STORED COMMENT '거래금액(숫자)',
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
CREATE TABLE user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 ID',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT '이메일',
    password VARCHAR(255) COMMENT '암호화된 비밀번호 (OAuth 사용자는 NULL 가능)',
    name VARCHAR(50) NOT NULL COMMENT '이름',
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

    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
    UNIQUE KEY uk_user_apt (user_id, apt_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='관심 아파트 테이블';

-- ============================================================================
-- 4. Restore Settings
-- ============================================================================

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;

-- ============================================================================
-- 5. Verify Tables Created
-- ============================================================================

SHOW TABLES;

-- ============================================================================
-- End of Schema DDL
-- ============================================================================
