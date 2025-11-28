-- USE jipjung; -- MySQL에서만 필요

-- User 테이블
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_user_email ON `user`(email);

-- 아파트 실거래가 테이블
CREATE TABLE IF NOT EXISTS apartment_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    apartment_name VARCHAR(100) NOT NULL COMMENT '아파트명',
    legal_dong VARCHAR(100) NOT NULL COMMENT '법정동',
    road_address VARCHAR(255) COMMENT '도로명 주소',
    build_year INT COMMENT '건축년도',
    deal_amount BIGINT NOT NULL COMMENT '거래금액(만원)',
    deal_date DATE NOT NULL COMMENT '거래일',
    exclusive_area DECIMAL(10, 2) NOT NULL COMMENT '전용면적(m²)',
    floor INT COMMENT '층',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 관심 아파트 테이블
CREATE TABLE IF NOT EXISTS favorite_apartment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    apartment_transaction_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    FOREIGN KEY (apartment_transaction_id) REFERENCES apartment_transaction(id) ON DELETE CASCADE,
    UNIQUE KEY unique_favorite (user_id, apartment_transaction_id)
);

-- 인덱스 생성
CREATE INDEX idx_apartment_legal_dong ON apartment_transaction(legal_dong);
CREATE INDEX idx_apartment_deal_date ON apartment_transaction(deal_date);
CREATE INDEX idx_favorite_user_id ON favorite_apartment(user_id);

-- 더미 데이터: 아파트 실거래가
INSERT INTO apartment_transaction (apartment_name, legal_dong, road_address, build_year, deal_amount, deal_date, exclusive_area, floor) VALUES
('래미안대치팰리스', '서울특별시 강남구 대치동', '서울특별시 강남구 삼성로 112', 2006, 250000, '2024-01-15', 114.50, 12),
('아크로리버파크', '서울특별시 서초구 잠원동', '서울특별시 서초구 신반포로 194', 2020, 185000, '2024-01-20', 84.92, 8),
('헬리오시티', '서울특별시 송파구 거여동', '서울특별시 송파구 양산로 335', 2019, 95000, '2024-02-05', 59.88, 15),
('e편한세상광교', '경기도 수원시 영통구 하동', '경기도 수원시 영통구 광교중앙로 248', 2012, 75000, '2024-02-10', 84.00, 10),
('디에이치자이개포', '서울특별시 강남구 개포동', '서울특별시 강남구 개포로 621', 2015, 162000, '2024-02-18', 101.87, 5),
('더샵센트럴시티', '서울특별시 마포구 공덕동', '서울특별시 마포구 마포대로 109', 2017, 123000, '2024-03-01', 74.50, 18),
('롯데캐슬골드파크', '서울특별시 구로구 구로동', '서울특별시 구로구 디지털로 242', 2011, 68000, '2024-03-10', 84.93, 7),
('힐스테이트광교산', '경기도 수원시 장안구 천천동', '경기도 수원시 장안구 수성로 308', 2018, 82000, '2024-03-15', 99.87, 14),
('자이문정', '서울특별시 송파구 문정동', '서울특별시 송파구 법원로 128', 2016, 89000, '2024-03-22', 84.95, 9),
('푸르지오시티', '서울특별시 강서구 등촌동', '서울특별시 강서구 강서로 392', 2013, 72000, '2024-04-01', 84.00, 11),
('래미안영등포센트레빌', '서울특별시 영등포구 영등포동', '서울특별시 영등포구 영등포로 257', 2021, 145000, '2024-04-10', 84.99, 20),
('호반써밋플레이스', '경기도 성남시 분당구 정자동', '경기도 성남시 분당구 불정로 90', 2014, 98000, '2024-04-18', 114.90, 6),
('e편한세상금천', '서울특별시 금천구 독산동', '서울특별시 금천구 범안로 1121', 2019, 71000, '2024-04-25', 59.95, 13),
('위례자이', '경기도 성남시 수정구 창곡동', '경기도 성남시 수정구 위례광장로 320', 2016, 88000, '2024-05-05', 84.97, 16),
('힐스테이트반포리버뷰', '서울특별시 서초구 반포동', '서울특별시 서초구 신반포로11길 32', 2022, 198000, '2024-05-12', 99.99, 22);
