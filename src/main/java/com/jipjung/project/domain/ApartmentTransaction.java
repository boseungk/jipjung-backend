package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApartmentTransaction {
    private Long id;
    private String apartmentName;
    private String legalDong;
    private String roadAddress;
    private Integer buildYear;
    private Long dealAmount;
    private LocalDate dealDate;
    private BigDecimal exclusiveArea;
    private Integer floor;
    private LocalDateTime createdAt;
}
