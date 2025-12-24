package com.jipjung.project.repository;

import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.controller.dto.response.RegionCoordinatesResponse;
import com.jipjung.project.domain.Apartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 아파트 및 실거래 정보 Mapper
 * schema.sql의 apartment, apartment_deal 테이블 접근
 */
@Mapper
public interface ApartmentMapper {

    /**
     * 아파트 목록 조회 (최신 실거래 1건씩 포함)
     * @param request 검색 조건 및 페이징 정보
     * @return 아파트 + 최신 거래 리스트
     */
    List<Apartment> findAllWithLatestDeal(@Param("request") ApartmentSearchRequest request);

    /**
     * 아파트 상세 조회 (모든 실거래 이력 포함)
     * @param aptSeq 아파트코드
     * @return 아파트 + 모든 거래 이력
     */
    Optional<Apartment> findByAptSeqWithDeals(@Param("aptSeq") String aptSeq);

    /**
     * 아파트 기본정보만 조회
     * @param aptSeq 아파트코드
     * @return 아파트 기본정보
     */
    Optional<Apartment> findByAptSeq(@Param("aptSeq") String aptSeq);

    /**
     * 아파트 존재 여부 확인
     * @param aptSeq 아파트코드
     * @return 존재 여부
     */
    boolean existsByAptSeq(@Param("aptSeq") String aptSeq);

    /**
     * 검색 조건에 맞는 아파트 개수 조회 (페이징용)
     * @param request 검색 조건
     * @return 아파트 개수
     */
    int count(@Param("request") ApartmentSearchRequest request);

    /**
     * 지역명으로 평균 좌표 조회
     * @param regionName 지역명 (구/군명, 예: 강남구)
     * @return 평균 좌표 (없으면 null)
     */
    @Select("""
        SELECT 
            #{regionName} AS region,
            AVG(a.latitude) AS latitude,
            AVG(a.longitude) AS longitude
        FROM apartment a
        JOIN dongcode d ON a.dong_code = d.dong_code
        WHERE TRIM(d.gugun_name) = #{regionName}
          AND a.latitude IS NOT NULL 
          AND a.longitude IS NOT NULL
        """)
    RegionCoordinatesResponse findAverageCoordinatesByRegion(@Param("regionName") String regionName);

    /**
     * 좌표가 없는 아파트 조회 (배치 지오코딩용)
     */
    List<Apartment> findMissingCoordinates(@Param("limit") int limit);

    /**
     * 아파트 정보 Upsert (있으면 업데이트, 없으면 삽입)
     * MOLIT API 동기화에 사용
     *
     * @param apartment 아파트 정보
     * @return 영향받은 행 수
     */
    int upsert(Apartment apartment);

    /**
     * 아파트 정보 삽입
     *
     * @param apartment 아파트 정보
     * @return 영향받은 행 수
     */
    int insert(Apartment apartment);

    /**
     * 아파트 좌표 및 법정동 코드 업데이트
     */
    int updateLocationIfMissing(
            @Param("aptSeq") String aptSeq,
            @Param("dongCode") String dongCode,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("longitude") java.math.BigDecimal longitude
    );
}
