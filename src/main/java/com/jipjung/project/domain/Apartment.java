package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 아파트 기본정보 도메인 모델
 * schema.sql의 apartment 테이블에 매핑
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Apartment {
    private String aptSeq;           // 아파트코드 (PK)
    private String dongCode;         // 법정동코드 (FK)
    private String sggCd;            // 시군구코드
    private String umdCd;            // 읍면동코드
    private String umdNm;            // 읍면동명
    private String jibun;            // 지번
    private String roadNmSggCd;      // 도로명시군구코드
    private String roadNm;           // 도로명
    private String roadNmBonbun;     // 도로명번호(본번)
    private String roadNmBubun;      // 도로명번호(부번)
    private String aptNm;            // 아파트명
    private Integer buildYear;       // 건축년도
    private BigDecimal latitude;     // 위도
    private BigDecimal longitude;    // 경도
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 조회 시 사용할 지역 정보 (dongcode 조인 결과)
    private String sidoName;      // 시도명 (예: 서울특별시, 광주광역시)
    private String sigunguName;   // 구군명 (예: 강남구, 서구)

    // 조회 시 사용할 실거래 정보 (조인 결과)
    private ApartmentDeal latestDeal;      // 최신 실거래 1건 (목록용)
    private List<ApartmentDeal> deals;     // 모든 실거래 이력 (상세용)
}
