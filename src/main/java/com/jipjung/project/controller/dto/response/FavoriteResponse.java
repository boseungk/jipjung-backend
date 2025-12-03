package com.jipjung.project.controller.dto.response;

import com.jipjung.project.domain.FavoriteApartment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관심 아파트 응답 DTO
 */
public record FavoriteResponse(
        Long id,
        String aptSeq,
        String aptNm,
        String umdNm,
        String roadNm,
        Integer buildYear,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime createdAt
) {
    public static FavoriteResponse from(FavoriteApartment favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getAptSeq(),
                favorite.getApartment() != null ? favorite.getApartment().getAptNm() : null,
                favorite.getApartment() != null ? favorite.getApartment().getUmdNm() : null,
                favorite.getApartment() != null ? favorite.getApartment().getRoadNm() : null,
                favorite.getApartment() != null ? favorite.getApartment().getBuildYear() : null,
                favorite.getApartment() != null ? favorite.getApartment().getLatitude() : null,
                favorite.getApartment() != null ? favorite.getApartment().getLongitude() : null,
                favorite.getCreatedAt()
        );
    }
}
