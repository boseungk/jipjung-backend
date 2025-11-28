package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteApartment {
    private Long id;
    private Long userId;
    private Long apartmentTransactionId;
    private LocalDateTime createdAt;

    // 조회 시 사용할 아파트 정보 (조인 결과)
    private ApartmentTransaction apartmentTransaction;
}
