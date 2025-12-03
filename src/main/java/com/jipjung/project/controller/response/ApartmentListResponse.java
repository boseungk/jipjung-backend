package com.jipjung.project.controller.response;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.ApartmentDeal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 아파트 목록 조회 응답 DTO
 * 아파트 기본정보 + 최신 실거래 1건
 */
public record ApartmentListResponse(
        // 아파트 기본정보
        String aptSeq,
        String aptNm,
        String umdNm,
        String roadNm,
        Integer buildYear,
        BigDecimal latitude,
        BigDecimal longitude,

        // 최신 실거래 정보
        Long dealNo,
        LocalDate dealDate,
        Long dealAmount,
        BigDecimal exclusiveArea,
        String floor
) {
    public static ApartmentListResponse from(Apartment apartment, ApartmentDeal latestDeal) {
        Optional<ApartmentDeal> deal = Optional.ofNullable(latestDeal);

        return new ApartmentListResponse(
                apartment.getAptSeq(),
                apartment.getAptNm(),
                apartment.getUmdNm(),
                apartment.getRoadNm(),
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
