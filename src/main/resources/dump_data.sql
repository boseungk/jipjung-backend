-- ============================================================================
-- SSAFYHome Sample Data
-- ============================================================================
-- Description: Sample data for testing and development
-- Version: 1.0
-- Author: SSAFY Team
-- Created: 2025-11-07
-- ============================================================================

USE ssafyhome;

-- Disable foreign key checks for faster insertion
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. Clear Existing Data (in reverse dependency order)
-- ============================================================================

TRUNCATE TABLE user_house_trees;
TRUNCATE TABLE user_levels;
TRUNCATE TABLE price_history;
TRUNCATE TABLE dsr_calculations;
TRUNCATE TABLE saving_records;
TRUNCATE TABLE dream_homes;
TRUNCATE TABLE preferred_areas;
TRUNCATE TABLE house_trees;
TRUNCATE TABLE policy_history;
TRUNCATE TABLE properties;
TRUNCATE TABLE users;

-- ============================================================================
-- 2. Independent Tables - Master Data
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 2.1 users - Sample Users
-- ----------------------------------------------------------------------------
INSERT INTO users (email, password, name, birth_year, annual_income, existing_loan_monthly, phone, is_active, onboarding_completed, consent_marketing) VALUES
('kim.ssafy@example.com', '$2a$10$XYZ...encrypted...hash', '김싸피', 1995, 50000000, 200000, '010-1234-5678', TRUE, TRUE, TRUE),
('lee.home@example.com', '$2a$10$ABC...encrypted...hash', '이집마련', 1998, 45000000, 0, '010-2345-6789', TRUE, TRUE, FALSE),
('park.dream@example.com', '$2a$10$DEF...encrypted...hash', '박드림', 1992, 60000000, 300000, '010-3456-7890', TRUE, TRUE, TRUE);

-- ----------------------------------------------------------------------------
-- 2.2 properties - Sample Properties
-- ----------------------------------------------------------------------------
INSERT INTO properties (apt_name, sido, gugun, dong, jibun, building_year, exclusive_area, floor, price, trade_date, subway_station, subway_line, subway_distance, walking_time_to_subway, recent_price_trend, data_source) VALUES
('래미안강동푸르지오', '서울특별시', '강동구', '고덕동', '430-10', 2020, 84.95, 12, 42000, '2025-08-15', '고덕역', '9호선', 400, 5, 'UP', '국토부'),
('고덕센트럴아이파크', '서울특별시', '강동구', '상일동', '189-1', 2022, 74.88, 8, 47000, '2025-09-20', '상일역', '5호선', 300, 4, 'STABLE', '국토부'),
('헬리오시티', '서울특별시', '송파구', '거여동', '274', 2021, 84.93, 15, 55000, '2025-10-01', '거여역', '5호선', 200, 3, 'UP', '국토부'),
('위례자이', '경기도', '성남시', '수정구', '창곡동', 2019, 84.99, 10, 38000, '2025-07-10', '복정역', '8호선', 500, 6, 'STABLE', '국토부'),
('송파파크하비오', '서울특별시', '송파구', '잠실동', '178-3', 2018, 59.92, 5, 48000, '2025-09-05', '잠실역', '2호선', 250, 3, 'DOWN', '국토부');

-- ----------------------------------------------------------------------------
-- 2.3 policy_history - Financial Policy History
-- ----------------------------------------------------------------------------
INSERT INTO policy_history (effective_date, dsr_limit, stress_rate_metro, stress_rate_non_metro, stress_stage, policy_name, description, is_current) VALUES
('2024-01-01', 40.00, 0.38, 0.19, 1, '스트레스 DSR 1단계', '스트레스 DSR 제도 1단계 시행', FALSE),
('2024-09-01', 40.00, 0.75, 0.38, 2, '스트레스 DSR 2단계', '스트레스 DSR 제도 2단계 강화', FALSE),
('2025-07-01', 40.00, 1.50, 0.75, 3, '스트레스 DSR 3단계 (전면시행)', '스트레스 DSR 제도 3단계 전면 시행', TRUE);

-- ----------------------------------------------------------------------------
-- 2.4 house_trees - House Tree Master Data
-- ----------------------------------------------------------------------------
INSERT INTO house_trees (tree_code, tree_name, tree_season, tree_description, animation_url, thumbnail_url, animation_duration_ms, file_size_kb, unlock_condition, required_level, required_savings_amount, rarity) VALUES
('spring_sakura_lv1', '봄 벚꽃지붕 집나무', 'SPRING', '봄날의 따뜻한 햇살을 머금은 벚꽃 집나무', 'https://cdn.ssafyhome.com/plome/spring_sakura_lv1.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/spring_sakura_lv1.jpg', 3000, 280, '첫 저축 달성', 1, 100000, 'COMMON'),
('summer_grass_lv2', '여름 잔디벽 집나무', 'SUMMER', '푸른 잔디로 덮인 시원한 여름 집나무', 'https://cdn.ssafyhome.com/plome/summer_grass_lv2.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/summer_grass_lv2.jpg', 3000, 290, '레벨 2 달성', 2, 500000, 'COMMON'),
('fall_terrace_lv3', '가을 테라스 집나무', 'FALL', '단풍이 아름다운 가을 테라스 집나무', 'https://cdn.ssafyhome.com/plome/fall_terrace_lv3.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/fall_terrace_lv3.jpg', 3500, 310, '레벨 3 달성 + 총 100만원 저축', 3, 1000000, 'RARE'),
('winter_chimney_lv1', '겨울 눈굴뚝 집나무', 'WINTER', '하얀 눈으로 덮인 포근한 겨울 집나무', 'https://cdn.ssafyhome.com/plome/winter_chimney_lv1.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/winter_chimney_lv1.jpg', 3000, 275, '12월 저축 달성', 1, 100000, 'COMMON'),
('spring_flower_lv2', '봄 꽃정원 집나무', 'SPRING', '형형색색 꽃이 만발한 봄 정원 집나무', 'https://cdn.ssafyhome.com/plome/spring_flower_lv2.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/spring_flower_lv2.jpg', 3200, 295, '레벨 2 달성 + 연속 저축 10일', 2, 500000, 'RARE'),
('summer_pool_lv3', '여름 수영장 집나무', 'SUMMER', '시원한 수영장이 있는 여름 집나무', 'https://cdn.ssafyhome.com/plome/summer_pool_lv3.webp', 'https://cdn.ssafyhome.com/plome/thumbnails/summer_pool_lv3.jpg', 3500, 320, '레벨 3 달성 + 총 200만원 저축', 3, 2000000, 'EPIC');

-- ============================================================================
-- 3. Dependent Tables - Transactional Data
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1 preferred_areas - User Preferred Areas
-- ----------------------------------------------------------------------------
INSERT INTO preferred_areas (user_id, sido, gugun, dong, priority) VALUES
-- 김싸피의 희망 지역
(1, '서울특별시', '강동구', '고덕동', 1),
(1, '서울특별시', '강동구', '상일동', 2),
(1, '경기도', '성남시', '수정구', 3),

-- 이집마련의 희망 지역
(2, '서울특별시', '송파구', '잠실동', 1),
(2, '서울특별시', '송파구', '거여동', 2),

-- 박드림의 희망 지역
(3, '서울특별시', '강남구', '역삼동', 1),
(3, '서울특별시', '서초구', '서초동', 2),
(3, '경기도', '성남시', '분당구', 3);

-- ----------------------------------------------------------------------------
-- 3.2 dream_homes - Dream Home Settings
-- ----------------------------------------------------------------------------
INSERT INTO dream_homes (user_id, property_id, target_amount, target_date, monthly_goal, down_payment_ratio, estimated_loan_amount, status, is_current, activated_at) VALUES
-- 김싸피: 래미안강동푸르지오 목표 (4.2억 * 30% = 1.26억, 24개월 = 월 525만원)
(1, 1, 126000000, '2027-10-01', 5250000, 30.00, 294000000, 'ACTIVE', TRUE, '2025-10-01'),

-- 이집마련: 헬리오시티 목표 (5.5억 * 30% = 1.65억, 30개월 = 월 550만원)
(2, 3, 165000000, '2028-03-01', 5500000, 30.00, 385000000, 'ACTIVE', TRUE, '2025-09-15');

-- ----------------------------------------------------------------------------
-- 3.3 saving_records - Saving Records
-- ----------------------------------------------------------------------------
INSERT INTO saving_records (user_id, amount, saving_method, saved_date, memo) VALUES
-- 김싸피의 저축 기록
(1, 5000000, 'KB청년희망적금', '2025-10-01', '월급날 자동이체'),
(1, 6000000, 'KB청년희망적금', '2025-11-01', '보너스 일부 추가 저축'),
(1, 5200000, 'KB청년희망적금', '2025-12-01', '정기 저축'),
(1, 5300000, 'KB청년희망적금', '2026-01-01', '신년 목표 달성'),
(1, 5000000, 'KB청년희망적금', '2026-02-01', '정기 저축'),

-- 이집마련의 저축 기록
(2, 5500000, '자유적금', '2025-09-15', '첫 저축 시작'),
(2, 5500000, '자유적금', '2025-10-15', '월별 저축'),
(2, 6000000, '자유적금', '2025-11-15', '추가 보너스 저축'),
(2, 5500000, '자유적금', '2025-12-15', '연말 정기 저축'),

-- 박드림의 저축 기록
(3, 7000000, '우리청년도약계좌', '2025-08-01', '고소득자 적극 저축'),
(3, 7000000, '우리청년도약계좌', '2025-09-01', '월별 저축'),
(3, 8000000, '우리청년도약계좌', '2025-10-01', '프로젝트 보너스 추가'),
(3, 7000000, '우리청년도약계좌', '2025-11-01', '정기 저축');

-- ----------------------------------------------------------------------------
-- 3.4 dsr_calculations - DSR Calculation History
-- ----------------------------------------------------------------------------
INSERT INTO dsr_calculations (user_id, annual_income, existing_loan_monthly, loan_amount, interest_rate, stress_rate, loan_period_years, monthly_payment, total_monthly_debt, dsr_ratio, max_loan_amount, is_approved, policy_id) VALUES
-- 김싸피의 DSR 계산 이력
(1, 50000000, 200000, 300000000, 4.50, 1.50, 30, 1800000, 2000000, 48.00, 270000000, FALSE, 3),
(1, 50000000, 200000, 270000000, 4.50, 1.50, 30, 1620000, 1820000, 43.68, 270000000, FALSE, 3),
(1, 50000000, 200000, 250000000, 4.50, 1.50, 30, 1500000, 1700000, 40.80, 270000000, FALSE, 3),

-- 이집마련의 DSR 계산 이력
(2, 45000000, 0, 400000000, 4.50, 1.50, 30, 2400000, 2400000, 64.00, 310000000, FALSE, 3),
(2, 45000000, 0, 310000000, 4.50, 1.50, 30, 1860000, 1860000, 49.60, 310000000, FALSE, 3),
(2, 45000000, 0, 280000000, 4.50, 1.50, 30, 1680000, 1680000, 44.80, 310000000, FALSE, 3),

-- 박드림의 DSR 계산 이력
(3, 60000000, 300000, 350000000, 4.50, 1.50, 30, 2100000, 2400000, 48.00, 350000000, FALSE, 3),
(3, 60000000, 300000, 320000000, 4.50, 1.50, 30, 1920000, 2220000, 44.40, 350000000, FALSE, 3);

-- ----------------------------------------------------------------------------
-- 3.5 price_history - Property Price History
-- ----------------------------------------------------------------------------
INSERT INTO price_history (property_id, price, trade_date, trade_type, price_change_amount, price_change_ratio) VALUES
-- 래미안강동푸르지오 가격 추이
(1, 40000, '2025-05-15', '매매', NULL, NULL),
(1, 40500, '2025-06-20', '매매', 500, 1.25),
(1, 41000, '2025-07-10', '매매', 500, 1.23),
(1, 42000, '2025-08-15', '매매', 1000, 2.44),

-- 고덕센트럴아이파크 가격 추이
(2, 45000, '2025-06-01', '매매', NULL, NULL),
(2, 46000, '2025-07-15', '매매', 1000, 2.22),
(2, 47000, '2025-09-20', '매매', 1000, 2.17),

-- 헬리오시티 가격 추이
(3, 53000, '2025-07-05', '매매', NULL, NULL),
(3, 54000, '2025-08-12', '매매', 1000, 1.89),
(3, 55000, '2025-10-01', '매매', 1000, 1.85),

-- 위례자이 가격 추이
(4, 36000, '2025-04-10', '매매', NULL, NULL),
(4, 37000, '2025-05-20', '매매', 1000, 2.78),
(4, 38000, '2025-07-10', '매매', 1000, 2.70),

-- 송파파크하비오 가격 추이
(5, 50000, '2025-06-15', '매매', NULL, NULL),
(5, 49000, '2025-07-25', '매매', -1000, -2.00),
(5, 48000, '2025-09-05', '매매', -1000, -2.04);

-- ============================================================================
-- 4. Gamification Tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 4.1 user_levels - User Level Data
-- ----------------------------------------------------------------------------
INSERT INTO user_levels (user_id, current_level, experience_points, level_title, total_savings_amount, total_saving_days, current_streak, longest_streak, next_level_threshold) VALUES
(1, 3, 280, '꾸준한 실천가', 26500000, 5, 5, 30, 300),
(2, 2, 150, '실천하는 저축가', 22500000, 4, 4, 10, 200),
(3, 4, 420, '저축 달인', 29000000, 4, 4, 15, 500);

-- ----------------------------------------------------------------------------
-- 4.2 user_house_trees - User House Tree Collection
-- ----------------------------------------------------------------------------
INSERT INTO user_house_trees (user_id, tree_id, tree_level, is_displayed_in_forest, acquired_at) VALUES
-- 김싸피가 획득한 집나무
(1, 1, 2, TRUE, '2025-10-01'),  -- 봄 벚꽃지붕 집나무 (레벨 2)
(1, 2, 1, TRUE, '2025-11-01'),  -- 여름 잔디벽 집나무 (레벨 1)
(1, 3, 1, TRUE, '2025-12-01'),  -- 가을 테라스 집나무 (레벨 1)

-- 이집마련이 획득한 집나무
(2, 1, 1, TRUE, '2025-09-15'),  -- 봄 벚꽃지붕 집나무 (레벨 1)
(2, 2, 1, TRUE, '2025-10-15'),  -- 여름 잔디벽 집나무 (레벨 1)

-- 박드림이 획득한 집나무
(3, 1, 3, TRUE, '2025-08-01'),  -- 봄 벚꽃지붕 집나무 (레벨 3)
(3, 2, 2, TRUE, '2025-09-01'),  -- 여름 잔디벽 집나무 (레벨 2)
(3, 3, 1, TRUE, '2025-10-01'),  -- 가을 테라스 집나무 (레벨 1)
(3, 5, 1, TRUE, '2025-11-01');  -- 봄 꽃정원 집나무 (레벨 1)

-- ============================================================================
-- 5. Data Verification
-- ============================================================================

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Display record counts
SELECT 'users' as table_name, COUNT(*) as record_count FROM users
UNION ALL
SELECT 'preferred_areas', COUNT(*) FROM preferred_areas
UNION ALL
SELECT 'properties', COUNT(*) FROM properties
UNION ALL
SELECT 'policy_history', COUNT(*) FROM policy_history
UNION ALL
SELECT 'house_trees', COUNT(*) FROM house_trees
UNION ALL
SELECT 'dream_homes', COUNT(*) FROM dream_homes
UNION ALL
SELECT 'saving_records', COUNT(*) FROM saving_records
UNION ALL
SELECT 'dsr_calculations', COUNT(*) FROM dsr_calculations
UNION ALL
SELECT 'price_history', COUNT(*) FROM price_history
UNION ALL
SELECT 'user_levels', COUNT(*) FROM user_levels
UNION ALL
SELECT 'user_house_trees', COUNT(*) FROM user_house_trees;

-- ============================================================================
-- End of Sample Data
-- ============================================================================
