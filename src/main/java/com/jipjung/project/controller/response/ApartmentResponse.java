package com.jipjung.project.controller.response;

import com.jipjung.project.domain.ApartmentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "아파트 실거래가 응답")
public record ApartmentResponse(
        @Schema(description = "아파트 실거래가 ID", example = "1")
        Long id,

        @Schema(description = "아파트명", example = "래미안대치팰리스")
        String apartmentName,

        @Schema(description = "법정동", example = "서울특별시 강남구 대치동")
        String legalDong,

        @Schema(description = "도로명 주소", example = "서울특별시 강남구 삼성로 112")
        String roadAddress,

        @Schema(description = "건축년도", example = "2006")
        Integer buildYear,

        @Schema(description = "거래금액 (만원)", example = "250000")
        Long dealAmount,

        @Schema(description = "거래일", example = "2024-01-15")
        LocalDate dealDate,

        @Schema(description = "전용면적 (m²)", example = "114.50")
        BigDecimal exclusiveArea,

        @Schema(description = "층", example = "12")
        Integer floor
) {
    public static ApartmentResponse from(ApartmentTransaction transaction) {
        return new ApartmentResponse(
                transaction.getId(),
                transaction.getApartmentName(),
                transaction.getLegalDong(),
                transaction.getRoadAddress(),
                transaction.getBuildYear(),
                transaction.getDealAmount(),
                transaction.getDealDate(),
                transaction.getExclusiveArea(),
                transaction.getFloor()
        );
    }
}
