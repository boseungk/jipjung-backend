-- ============================================================================
-- JipJung Database Validation Queries
-- ============================================================================
-- Description: 데이터베이스 검증 쿼리 모음
-- Version: 1.0
-- Created: 2025-11-28
-- ============================================================================

-- Character Set 설정
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

USE jipjung;

-- ============================================================================
-- 1. 데이터 카운트 검증
-- ============================================================================

SELECT 'dongcode' AS table_name, COUNT(*) AS count FROM dongcode
UNION ALL
SELECT 'apartment', COUNT(*) FROM apartment
UNION ALL
SELECT 'apartment_deal', COUNT(*) FROM apartment_deal
UNION ALL
SELECT 'user', COUNT(*) FROM user
UNION ALL
SELECT 'favorite_apartment', COUNT(*) FROM favorite_apartment;

-- Expected:
-- dongcode: 49
-- apartment: 20
-- apartment_deal: 48
-- user: 0 (초기 상태)
-- favorite_apartment: 0 (초기 상태)

-- ============================================================================
-- 2. FK 무결성 검증
-- ============================================================================

-- 2.1 apartment의 dong_code가 dongcode에 존재하는지 확인
SELECT COUNT(*) AS invalid_dong_code_count
FROM apartment a
LEFT JOIN dongcode d ON a.dong_code = d.dong_code
WHERE a.dong_code IS NOT NULL AND d.dong_code IS NULL;
-- Expected: 0

-- 2.2 apartment_deal의 apt_seq가 apartment에 존재하는지 확인
SELECT COUNT(*) AS invalid_apt_seq_count
FROM apartment_deal ad
LEFT JOIN apartment a ON ad.apt_seq = a.apt_seq
WHERE a.apt_seq IS NULL;
-- Expected: 0

-- 2.3 favorite_apartment의 user_id가 user에 존재하는지 확인
SELECT COUNT(*) AS invalid_user_id_count
FROM favorite_apartment fa
LEFT JOIN user u ON fa.user_id = u.user_id
WHERE u.user_id IS NULL;
-- Expected: 0 (데이터 있을 경우)

-- 2.4 favorite_apartment의 apt_seq가 apartment에 존재하는지 확인
SELECT COUNT(*) AS invalid_apt_seq_in_favorite_count
FROM favorite_apartment fa
LEFT JOIN apartment a ON fa.apt_seq = a.apt_seq
WHERE a.apt_seq IS NULL;
-- Expected: 0 (데이터 있을 경우)

-- ============================================================================
-- 3. 생성 컬럼 검증
-- ============================================================================

-- 3.1 deal_date가 정상적으로 생성되었는지
SELECT
    COUNT(*) AS invalid_deal_date_count,
    'deal_date NULL or invalid' AS issue
FROM apartment_deal
WHERE deal_date IS NULL OR YEAR(deal_date) = 0;
-- Expected: 0

-- 3.2 deal_amount_num이 정상적으로 생성되었는지
SELECT
    COUNT(*) AS invalid_deal_amount_num_count,
    'deal_amount_num NULL or 0' AS issue
FROM apartment_deal
WHERE deal_amount_num IS NULL OR deal_amount_num = 0;
-- Expected: 0

-- 3.3 생성 컬럼 샘플 확인
SELECT
    deal_year,
    deal_month,
    deal_day,
    deal_date,
    deal_amount,
    deal_amount_num
FROM apartment_deal
LIMIT 10;

-- ============================================================================
-- 4. 데이터 무결성 검증
-- ============================================================================

-- 4.1 필수 필드 NULL 체크 - apartment
SELECT COUNT(*) AS apartment_null_apt_nm_count
FROM apartment
WHERE apt_nm IS NULL OR apt_nm = '';
-- Expected: 0

-- 4.2 필수 필드 NULL 체크 - apartment_deal
SELECT COUNT(*) AS deal_null_fields_count
FROM apartment_deal
WHERE apt_seq IS NULL
   OR deal_year IS NULL
   OR deal_month IS NULL
   OR deal_day IS NULL
   OR exclu_use_ar IS NULL
   OR deal_amount IS NULL;
-- Expected: 0

-- 4.3 필수 필드 NULL 체크 - user
SELECT COUNT(*) AS user_null_fields_count
FROM user
WHERE email IS NULL
   OR name IS NULL;
-- Expected: 0 (데이터 있을 경우)

-- ============================================================================
-- 5. 중복 데이터 검증
-- ============================================================================

-- 5.1 dongcode 중복 체크
SELECT dong_code, COUNT(*) AS count
FROM dongcode
GROUP BY dong_code
HAVING count > 1;
-- Expected: 0 rows

-- 5.2 apartment 중복 체크
SELECT apt_seq, COUNT(*) AS count
FROM apartment
GROUP BY apt_seq
HAVING count > 1;
-- Expected: 0 rows

-- 5.3 user email 중복 체크
SELECT email, COUNT(*) AS count
FROM user
GROUP BY email
HAVING count > 1;
-- Expected: 0 rows

-- 5.4 user social 중복 체크
SELECT social_provider, social_id, COUNT(*) AS count
FROM user
WHERE social_provider IS NOT NULL AND social_id IS NOT NULL
GROUP BY social_provider, social_id
HAVING count > 1;
-- Expected: 0 rows

-- 5.5 favorite_apartment 중복 체크
SELECT user_id, apt_seq, COUNT(*) AS count
FROM favorite_apartment
GROUP BY user_id, apt_seq
HAVING count > 1;
-- Expected: 0 rows

-- ============================================================================
-- 6. 인덱스 검증
-- ============================================================================

-- 6.1 dongcode 인덱스 확인
SHOW INDEX FROM dongcode;

-- 6.2 apartment 인덱스 확인
SHOW INDEX FROM apartment;

-- 6.3 apartment_deal 인덱스 확인
SHOW INDEX FROM apartment_deal;

-- 6.4 user 인덱스 확인
SHOW INDEX FROM user;

-- 6.5 favorite_apartment 인덱스 확인
SHOW INDEX FROM favorite_apartment;

-- ============================================================================
-- 7. 샘플 데이터 조회
-- ============================================================================

-- 7.1 특정 아파트의 거래 이력 (27230-174: 한일)
SELECT
    ad.deal_no,
    a.apt_nm,
    a.umd_nm,
    ad.apt_dong,
    ad.floor,
    ad.deal_date,
    ad.deal_amount,
    ad.deal_amount_num,
    ad.exclu_use_ar
FROM apartment_deal ad
JOIN apartment a ON ad.apt_seq = a.apt_seq
WHERE a.apt_seq = '27230-174'
ORDER BY ad.deal_date DESC;

-- 7.2 특정 아파트의 거래 이력 (11545-23: 금천현대)
SELECT
    ad.deal_no,
    a.apt_nm,
    a.umd_nm,
    ad.apt_dong,
    ad.floor,
    ad.deal_date,
    ad.deal_amount,
    ad.deal_amount_num,
    ad.exclu_use_ar
FROM apartment_deal ad
JOIN apartment a ON ad.apt_seq = a.apt_seq
WHERE a.apt_seq = '11545-23'
ORDER BY ad.deal_date DESC;

-- 7.3 최근 거래 TOP 10
SELECT
    ad.deal_no,
    a.apt_nm,
    a.umd_nm,
    ad.deal_date,
    ad.deal_amount_num,
    ad.exclu_use_ar,
    ad.floor
FROM apartment_deal ad
JOIN apartment a ON ad.apt_seq = a.apt_seq
ORDER BY ad.deal_date DESC
LIMIT 10;

-- 7.4 거래금액 상위 10건
SELECT
    ad.deal_no,
    a.apt_nm,
    a.umd_nm,
    ad.deal_date,
    ad.deal_amount_num,
    ad.exclu_use_ar,
    ad.floor
FROM apartment_deal ad
JOIN apartment a ON ad.apt_seq = a.apt_seq
ORDER BY ad.deal_amount_num DESC
LIMIT 10;

-- 7.5 아파트별 거래 건수
SELECT
    a.apt_seq,
    a.apt_nm,
    a.umd_nm,
    COUNT(ad.deal_no) AS deal_count,
    MIN(ad.deal_amount_num) AS min_price,
    MAX(ad.deal_amount_num) AS max_price,
    AVG(ad.deal_amount_num) AS avg_price
FROM apartment a
LEFT JOIN apartment_deal ad ON a.apt_seq = ad.apt_seq
GROUP BY a.apt_seq, a.apt_nm, a.umd_nm
ORDER BY deal_count DESC;

-- 7.6 위도/경도 정밀도 확인
SELECT
    apt_seq,
    apt_nm,
    latitude,
    longitude,
    CHAR_LENGTH(CAST(latitude AS CHAR)) AS lat_length,
    CHAR_LENGTH(CAST(longitude AS CHAR)) AS lng_length
FROM apartment
WHERE latitude IS NOT NULL AND longitude IS NOT NULL
LIMIT 5;

-- 7.7 dongcode별 아파트 수
SELECT
    d.dong_code,
    d.sido_name,
    d.gugun_name,
    d.dong_name,
    COUNT(a.apt_seq) AS apartment_count
FROM dongcode d
LEFT JOIN apartment a ON d.dong_code = a.dong_code
GROUP BY d.dong_code, d.sido_name, d.gugun_name, d.dong_name
ORDER BY apartment_count DESC;

-- ============================================================================
-- 8. 성능 테스트 쿼리
-- ============================================================================

-- 8.1 인덱스 사용 여부 확인 (EXPLAIN)
EXPLAIN SELECT * FROM apartment WHERE apt_nm LIKE '%한일%';

-- 8.2 deal_date 인덱스 사용 확인
EXPLAIN SELECT * FROM apartment_deal
WHERE deal_date BETWEEN '2020-01-01' AND '2024-12-31';

-- 8.3 deal_amount_num 인덱스 사용 확인
EXPLAIN SELECT * FROM apartment_deal
WHERE deal_amount_num BETWEEN 10000 AND 50000;

-- 8.4 JOIN 성능 확인
EXPLAIN SELECT
    ad.deal_no,
    a.apt_nm,
    ad.deal_date,
    ad.deal_amount_num
FROM apartment_deal ad
JOIN apartment a ON ad.apt_seq = a.apt_seq
WHERE ad.deal_date >= '2024-01-01'
  AND ad.deal_amount_num >= 50000;

-- ============================================================================
-- 9. 테이블 구조 확인
-- ============================================================================

-- 9.1 모든 테이블 목록
SHOW TABLES;

-- 9.2 dongcode 테이블 구조
DESC dongcode;

-- 9.3 apartment 테이블 구조
DESC apartment;

-- 9.4 apartment_deal 테이블 구조
DESC apartment_deal;

-- 9.5 user 테이블 구조
DESC user;

-- 9.6 favorite_apartment 테이블 구조
DESC favorite_apartment;

-- ============================================================================
-- End of Validation Queries
-- ============================================================================
