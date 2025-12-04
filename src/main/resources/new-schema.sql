-- ============================================================================
-- JipJung Database Schema v3.0 (Optimized)
-- ============================================================================
-- 1. 테마/디자인 분리
-- 2. 인덱스 최적화
-- 3. Audit 컬럼 (updated_at, is_deleted) 추가
-- ============================================================================

SET foreign_key_checks = 0;

-- 기존 테이블 Drop (역순)
DROP TABLE IF EXISTS expense_review;
DROP TABLE IF EXISTS expense;
DROP TABLE IF EXISTS savings_history;
DROP TABLE IF EXISTS dream_home;
DROP TABLE IF EXISTS favorite_apartment;
DROP TABLE IF EXISTS apartment_deal;
DROP TABLE IF EXISTS apartment;
DROP TABLE IF EXISTS dongcode;
DROP TABLE IF EXISTS theme_asset;
DROP TABLE IF EXISTS house_theme;
DROP TABLE IF EXISTS growth_level;
DROP TABLE IF EXISTS `user`;

SET foreign_key_checks = 1;

-- ============================================================================
-- [Part 1] 기초 데이터 & 게임 메타 (Theme & Rules)
-- ============================================================================

-- 1. 법정동 코드
CREATE TABLE dongcode (
                          dong_code VARCHAR(10) PRIMARY KEY,
                          sido_name VARCHAR(30) NOT NULL,
                          gugun_name VARCHAR(30) NOT NULL,
                          dong_name VARCHAR(30),
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 아파트 마스터
CREATE TABLE apartment (
                           apt_seq VARCHAR(20) PRIMARY KEY,
                           dong_code VARCHAR(10),
                           apt_nm VARCHAR(40) NOT NULL,
                           build_year INT,
                           jibun VARCHAR(10),
                           road_nm VARCHAR(20),
                           latitude DECIMAL(16, 13),
                           longitude DECIMAL(16, 13),
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                           FOREIGN KEY (dong_code) REFERENCES dongcode(dong_code),
                           INDEX idx_apt_nm (apt_nm),
                           INDEX idx_apt_dong (dong_code),
                           INDEX idx_apt_build_year (build_year),
                           INDEX idx_apt_geo (latitude, longitude) -- 지도 범위 검색용 인덱스
);

-- 3. [NEW] 성장 레벨 규칙 (Design 분리)
-- 이미지 URL을 제거하고, 순수 '성장 규칙'만 관리합니다.
CREATE TABLE growth_level (
                              level INT PRIMARY KEY COMMENT '레벨',
                              step_name VARCHAR(50) NOT NULL COMMENT '단계명 (터파기, 골조...)',
                              description VARCHAR(255),
                              required_exp INT NOT NULL COMMENT '다음 레벨업 필요 경험치',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              is_deleted BOOLEAN DEFAULT FALSE
);

-- 4. [NEW] 하우스 테마 (Theme)
-- 다양한 집 디자인(스킨)을 지원하기 위한 테이블
CREATE TABLE house_theme (
                             theme_id INT AUTO_INCREMENT PRIMARY KEY,
                             theme_code VARCHAR(20) UNIQUE NOT NULL COMMENT 'MODERN, HANOK, CASTLE...',
                             theme_name VARCHAR(50) NOT NULL,
                             is_active BOOLEAN DEFAULT TRUE COMMENT '현재 선택 가능 여부',
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             is_deleted BOOLEAN DEFAULT FALSE
);

-- 5. [NEW] 테마별 에셋 (Theme Assets)
-- (테마 + 레벨) 조합에 따른 이미지를 관리합니다.
CREATE TABLE theme_asset (
                             asset_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             theme_id INT NOT NULL,
                             level INT NOT NULL,
                             image_url VARCHAR(255) NOT NULL,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             is_deleted BOOLEAN DEFAULT FALSE,

                             FOREIGN KEY (theme_id) REFERENCES house_theme(theme_id) ON DELETE CASCADE,
                             FOREIGN KEY (level) REFERENCES growth_level(level) ON DELETE CASCADE,
                             UNIQUE KEY uk_theme_level (theme_id, level) -- 테마별 레벨 이미지는 유일해야 함
);

-- 초기 데이터 세팅 (예시)
-- INSERT INTO growth_level VALUES (1, '터파기', '...', 1000);
-- INSERT INTO house_theme VALUES (1, 'MODERN', '모던 아파트', true);
-- INSERT INTO theme_asset VALUES (NULL, 1, 1, '/img/modern/lv1.png');


-- ============================================================================
-- [Part 2] 사용자 (User)
-- ============================================================================

CREATE TABLE `user` (
                        user_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 계정
                        email VARCHAR(100) UNIQUE NOT NULL,
                        password VARCHAR(255),
                        nickname VARCHAR(50) NOT NULL,
                        profile_image_url VARCHAR(255),

    -- 소셜
                        social_provider VARCHAR(20),
                        social_id VARCHAR(100),

    -- 금융/온보딩
                        onboarding_completed BOOLEAN DEFAULT FALSE,
                        birth_year INT,
                        annual_income BIGINT DEFAULT 0,
                        existing_loan_monthly BIGINT DEFAULT 0,

                        -- [Game] 성장 및 테마
                        current_level INT DEFAULT 1,
                        current_exp INT DEFAULT 0,
                        streak_count INT DEFAULT 0,
                        selected_theme_id INT DEFAULT NULL COMMENT '현재 적용 중인 집 테마 FK',

    -- Audit
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT FALSE, -- 탈퇴 여부 (Soft Delete)

                        FOREIGN KEY (selected_theme_id) REFERENCES house_theme(theme_id),
                        INDEX idx_user_email (email)
);


-- ============================================================================
-- [Part 3] 부동산 & 목표 (Dream Home)
-- ============================================================================

-- 7. 실거래가
CREATE TABLE apartment_deal (
                                deal_no BIGINT AUTO_INCREMENT PRIMARY KEY,
                                apt_seq VARCHAR(20) NOT NULL,
                                deal_amount VARCHAR(20),
                                deal_amount_num BIGINT, -- 정렬/차트용 숫자 컬럼
                                deal_date DATE,
                                area DECIMAL(7,2),
                                floor INT,

                                FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
                                INDEX idx_deal_apt_date (apt_seq, deal_date) -- [차트] 아파트별 시세 추이 조회용
);

-- 8. 관심 아파트
CREATE TABLE favorite_apartment (
                                    favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    apt_seq VARCHAR(20) NOT NULL,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                    FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
                                    FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq) ON DELETE CASCADE,
                                    UNIQUE KEY uk_fav_user_apt (user_id, apt_seq)
);

-- 9. 드림홈 (목표)
CREATE TABLE dream_home (
                            dream_home_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            user_id BIGINT NOT NULL,
                            apt_seq VARCHAR(20) NOT NULL,

                            target_amount BIGINT NOT NULL,
                            target_date DATE NOT NULL,
                            monthly_goal BIGINT,

                            current_saved_amount BIGINT DEFAULT 0,
                            start_date DATE DEFAULT (CURRENT_DATE),
                            status ENUM('ACTIVE','COMPLETED','GIVEN_UP') DEFAULT 'ACTIVE' COMMENT 'ACTIVE, COMPLETED, GIVEN_UP',

    -- Audit
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            is_deleted BOOLEAN DEFAULT FALSE, -- 목표 삭제 시 (Soft Delete)

                            FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
                            FOREIGN KEY (apt_seq) REFERENCES apartment(apt_seq),

                            INDEX idx_dream_user_status (user_id, status) -- [대시보드] 활성화된 목표 바로 조회
);

-- 10. 저축 히스토리 (차트용 데이터)
CREATE TABLE savings_history (
                                 savings_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 dream_home_id BIGINT NOT NULL,

                                 amount BIGINT NOT NULL,
                                 save_type ENUM('DEPOSIT','WITHDRAW') NOT NULL COMMENT '저축/인출 구분',
                                 memo VARCHAR(100),

    -- Audit
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 저축 일시 (차트 X축)
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 수정 일시
                                 is_deleted BOOLEAN DEFAULT FALSE, -- 내역 삭제 (취소)

                                 FOREIGN KEY (dream_home_id) REFERENCES dream_home(dream_home_id) ON DELETE CASCADE,

                                 INDEX idx_save_home_date (dream_home_id, is_deleted, created_at) -- [차트] 기간별 저축 흐름 조회용
);


-- ============================================================================
-- [Part 4] AI 매니저 & 지출 (Core Game Loop)
-- ============================================================================

-- 11. 지출 원장
CREATE TABLE expense (
                         expense_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         user_id BIGINT NOT NULL,

                         amount BIGINT NOT NULL,
                         store_name VARCHAR(100),
                         category VARCHAR(50),
                         payment_date DATETIME NOT NULL, -- 실제 결제일
                         memo VARCHAR(255),
                         is_verified BOOLEAN DEFAULT FALSE,

    -- Audit
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         is_deleted BOOLEAN DEFAULT FALSE,

                         FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,

                         INDEX idx_exp_user_date (user_id, is_deleted, payment_date), -- [통계] 월별 지출 조회용
                         INDEX idx_exp_category (category) -- [통계] 카테고리별 분석용
);

-- 12. AI 심판 기록
CREATE TABLE expense_review (
                                review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                expense_id BIGINT NOT NULL,

                                user_excuse VARCHAR(255),
                                ai_result VARCHAR(20) NOT NULL, -- REASONABLE, WASTE
                                exp_change BIGINT NOT NULL,
                                ai_score INT,
                                ai_comment TEXT,
                                ai_mood VARCHAR(20),

                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                FOREIGN KEY (expense_id) REFERENCES expense(expense_id) ON DELETE CASCADE,
                                UNIQUE KEY uk_expense_review (expense_id)
);
-- 13. [NEW] 유저 컬렉션 (완성한 집/테마 목록)
CREATE TABLE user_collection (
                                 collection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 user_id BIGINT NOT NULL,
                                 theme_id INT NOT NULL,

                                 completed_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '완공 날짜',
                                 is_main_display BOOLEAN DEFAULT FALSE COMMENT '대표 컬렉션(메인 화면 전시) 여부',

                                 FOREIGN KEY (user_id) REFERENCES `user`(user_id) ON DELETE CASCADE,
                                 FOREIGN KEY (theme_id) REFERENCES house_theme(theme_id) ON DELETE CASCADE,
                                 INDEX idx_collection_user_theme_date (user_id, theme_id, completed_at)
);
