package com.jipjung.project.controller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아파트 실거래가 검색 요청")
public record ApartmentSearchRequest(
        @Schema(description = "법정동 (검색어)", example = "강남구")
        String legalDong,

        @Schema(description = "아파트명 (검색어)", example = "래미안")
        String apartmentName,

        @Schema(description = "거래일 시작일 (YYYY-MM-DD)", example = "2024-01-01")
        String dealDateFrom,

        @Schema(description = "거래일 종료일 (YYYY-MM-DD)", example = "2024-12-31")
        String dealDateTo,

        @Schema(description = "최소 거래금액 (만원)", example = "50000")
        Long minDealAmount,

        @Schema(description = "최대 거래금액 (만원)", example = "200000")
        Long maxDealAmount,

        @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
        Integer page,

        @Schema(description = "페이지 크기", example = "10")
        Integer size
) {
    public ApartmentSearchRequest {
        // 기본값 설정
        if (page == null) page = 0;
        if (size == null) size = 10;
    }
}
