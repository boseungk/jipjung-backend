package com.jipjung.project.controller.dto.request;

/**
 * 아파트 검색 요청 DTO
 * schema.sql의 apartment + apartment_deal 테이블 기준
 */
public record ApartmentSearchRequest(
        String aptNm,           // 아파트명 (apartment.apt_nm)
        String umdNm,           // 읍면동명 (apartment.umd_nm)
        String dealDateFrom,    // 거래일 시작 (apartment_deal.deal_date)
        String dealDateTo,      // 거래일 종료
        Long minDealAmount,     // 최소 거래금액 (만원, apartment_deal.deal_amount_num)
        Long maxDealAmount,     // 최대 거래금액 (만원)
        Integer page,           // 페이지 번호 (0부터 시작)
        Integer size            // 페이지 크기
) {
    public ApartmentSearchRequest {
        // 기본값 설정
        page = (page != null && page >= 0) ? page : 0;
        size = (size != null && size > 0 && size <= 100) ? size : 10;
    }
}
