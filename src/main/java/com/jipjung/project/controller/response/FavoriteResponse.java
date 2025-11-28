package com.jipjung.project.controller.response;

import com.jipjung.project.domain.FavoriteApartment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관심 아파트 응답")
public record FavoriteResponse(
        @Schema(description = "관심 아파트 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "아파트 실거래가 정보")
        ApartmentResponse apartment,

        @Schema(description = "등록일시", example = "2024-05-15T10:30:00")
        LocalDateTime createdAt
) {
    public static FavoriteResponse from(FavoriteApartment favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getUserId(),
                ApartmentResponse.from(favorite.getApartmentTransaction()),
                favorite.getCreatedAt()
        );
    }
}
