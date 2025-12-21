package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 아파트 검색 요청 DTO
 * schema.sql의 apartment + apartment_deal 테이블 기준
 */
public record ApartmentSearchRequest(
        @Schema(description = "검색어 (아파트명/읍면동/도로명)", example = "현대", nullable = true)
        String keyword,

        @Schema(description = "아파트명 (부분 검색)", example = "금천현대", nullable = true)
        String aptNm,

        @Schema(description = "읍면동명 (부분 검색)", example = "홍제동", nullable = true)
        String umdNm,

        @Schema(description = "시/도", example = "서울특별시", nullable = true)
        String sido,

        @Schema(description = "시/군/구", example = "강남구", nullable = true)
        String sigungu,

        @Schema(description = "법정동코드 앞 5자리 (Fallback 동기화용)", example = "11680", nullable = true)
        String lawdCd,

        @Schema(description = "거래일 시작 (YYYY-MM-DD)", example = "2020-01-01", nullable = true)
        String dealDateFrom,

        @Schema(description = "거래일 종료 (YYYY-MM-DD)", example = "2024-12-31", nullable = true)
        String dealDateTo,

        @Schema(description = "최소 거래금액 (만원)", example = "10000", nullable = true)
        Long minDealAmount,

        @Schema(description = "최대 거래금액 (만원)", example = "100000", nullable = true)
        Long maxDealAmount,

        @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
        Integer page,

        @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
        Integer size
) {
    public ApartmentSearchRequest {
        // 기본값 설정
        page = (page != null && page >= 0) ? page : 0;
        size = (size != null && size > 0 && size <= 100) ? size : 10;
    }
    public long getOffset() {
        return (long) page * size;
    }

    public ApartmentSearchRequest withLawdCd(String resolvedLawdCd) {
        return new ApartmentSearchRequest(
                keyword,
                aptNm,
                umdNm,
                sido,
                sigungu,
                resolvedLawdCd,
                dealDateFrom,
                dealDateTo,
                minDealAmount,
                maxDealAmount,
                page,
                size
        );
    }
}
