package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 아파트 실거래 정보 도메인 모델
 * schema.sql의 apartment_deal 테이블에 매핑
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApartmentDeal {
    private Long dealNo;             // 거래번호 (PK)
    private String aptSeq;           // 아파트코드 (FK)
    private String aptDong;          // 동
    private String floor;            // 층
    private Integer dealYear;        // 거래년도
    private Integer dealMonth;       // 거래월
    private Integer dealDay;         // 거래일
    private LocalDate dealDate;      // 거래일자 (생성컬럼)
    private BigDecimal excluUseAr;   // 전용면적(㎡)
    private String dealAmount;       // 거래금액(만원)
    private Long dealAmountNum;      // 거래금액(숫자, 생성컬럼)
    private LocalDateTime createdAt;
}
