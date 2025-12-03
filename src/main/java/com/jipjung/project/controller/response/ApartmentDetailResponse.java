package com.jipjung.project.controller.response;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.ApartmentDeal;

import java.math.BigDecimal;
import java.util.List;

/**
 * 아파트 상세 조회 응답 DTO
 * 아파트 기본정보 + 모든 실거래 이력
 */
public record ApartmentDetailResponse(
        // 아파트 기본정보
        String aptSeq,
        String aptNm,
        String umdNm,
        String jibun,
        String roadNm,
        String roadNmBonbun,
        String roadNmBubun,
        Integer buildYear,
        BigDecimal latitude,
        BigDecimal longitude,

        // 실거래 이력 목록
        List<DealInfo> deals
) {
    public record DealInfo(
            Long dealNo,
            String aptDong,
            String floor,
            java.time.LocalDate dealDate,
            Long dealAmount,
            BigDecimal exclusiveArea
    ) {
        public static DealInfo from(ApartmentDeal deal) {
            return new DealInfo(
                    deal.getDealNo(),
                    deal.getAptDong(),
                    deal.getFloor(),
                    deal.getDealDate(),
                    deal.getDealAmountNum(),
                    deal.getExcluUseAr()
            );
        }
    }

    public static ApartmentDetailResponse from(Apartment apartment, List<ApartmentDeal> deals) {
        List<DealInfo> dealInfos = deals != null
                ? deals.stream().map(DealInfo::from).toList()
                : List.of();

        return new ApartmentDetailResponse(
                apartment.getAptSeq(),
                apartment.getAptNm(),
                apartment.getUmdNm(),
                apartment.getJibun(),
                apartment.getRoadNm(),
                apartment.getRoadNmBonbun(),
                apartment.getRoadNmBubun(),
                apartment.getBuildYear(),
                apartment.getLatitude(),
                apartment.getLongitude(),
                dealInfos
        );
    }
}
