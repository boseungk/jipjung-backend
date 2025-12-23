package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.ApartmentDeal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 아파트 목록 조회 응답 DTO
 * 아파트 기본정보 + 최신 실거래 1건
 */
@Schema(description = "아파트 목록 응답 (기본정보 + 최신 실거래 1건)")
public record ApartmentListResponse(
        // 아파트 기본정보
        @Schema(description = "아파트 코드", example = "11410-61")
        String aptSeq,

        @Schema(description = "아파트명", example = "금천현대")
        String aptNm,

        @Schema(description = "읍면동명", example = "홍제동")
        String umdNm,

        @Schema(description = "도로명", example = "연희로")
        String roadNm,

        @Schema(description = "시/도", example = "서울특별시")
        String sido,

        @Schema(description = "시/군/구", example = "서대문구")
        String sigungu,

        @Schema(description = "건축년도", example = "2015")
        Integer buildYear,

        @Schema(description = "위도", example = "37.5689043200000")
        BigDecimal latitude,

        @Schema(description = "경도", example = "126.9341234000000")
        BigDecimal longitude,

        // 최신 실거래 정보
        @Schema(description = "거래번호", example = "12345", nullable = true)
        Long dealNo,

        @Schema(description = "거래일자 (YYYY-MM-DD)", example = "2024-11-15", nullable = true)
        LocalDate dealDate,

        @Schema(description = "거래금액 (만원)", example = "450000", nullable = true)
        Long dealAmount,

        @Schema(description = "전용면적 (㎡)", example = "84.5", nullable = true)
        BigDecimal exclusiveArea,

        @Schema(description = "층수", example = "5층", nullable = true)
        String floor
) {
    public static ApartmentListResponse from(Apartment apartment, ApartmentDeal latestDeal) {
        Optional<ApartmentDeal> deal = Optional.ofNullable(latestDeal);

        return new ApartmentListResponse(
            apartment.getAptSeq(),
            apartment.getAptNm(),
            apartment.getUmdNm(),
            apartment.getRoadNm(),
            apartment.getSidoName(),
            apartment.getSigunguName(),
            apartment.getBuildYear(),
            apartment.getLatitude(),
            apartment.getLongitude(),
            deal.map(ApartmentDeal::getDealNo).orElse(null),
            deal.map(ApartmentDeal::getDealDate).orElse(null),
            deal.map(ApartmentDeal::getDealAmountNum).orElse(null),
            deal.map(ApartmentDeal::getExcluUseAr).orElse(null),
            deal.map(ApartmentDeal::getFloor).orElse(null)
        );
    }
}
