package com.jipjung.project.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 아파트 거래 Mapper
 */
@Mapper
public interface ApartmentDealMapper {

    /**
     * 특정 구군의 최근 거래 평균가 조회 (원 단위로 변환: 만원 * 10000)
     * - 최근 1년 거래 기준
     * - 구군명으로 검색 (예: "강남구", "서초구")
     */
    @Select("""
        SELECT AVG(CAST(REPLACE(ad.deal_amount, ',', '') AS UNSIGNED) * 10000) AS avg_amount
        FROM apartment_deal ad
        JOIN apartment a ON ad.apt_seq = a.apt_seq
        JOIN dongcode d ON a.dong_code = d.dong_code
        WHERE (
            TRIM(d.gugun_name) = #{gugunName}
            OR CONCAT(TRIM(d.sido_name), ' ', TRIM(d.gugun_name)) = #{gugunName}
        )
          AND ad.deal_year >= YEAR(CURDATE()) - 1
    """)
    Long findAverageRecentDealAmountByGugun(@Param("gugunName") String gugunName);
}
