package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관심 아파트 도메인 모델
 * schema.sql의 favorite_apartment 테이블에 매핑
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteApartment {
    private Long id;            // favorite_id (PK)
    private Long userId;        // user_id (FK)
    private String aptSeq;      // apt_seq (FK)
    private LocalDateTime createdAt;

    // 조회 시 사용할 아파트 정보 (조인 결과)
    private Apartment apartment;
}
