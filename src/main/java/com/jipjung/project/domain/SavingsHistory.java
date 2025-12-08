package com.jipjung.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 저축 내역 도메인
 * - 입금(DEPOSIT) 및 출금(WITHDRAW) 기록
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsHistory {

    private Long savingsId;
    private Long dreamHomeId;
    private Long amount;
    private SaveType saveType;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;

    /**
     * 저축 타입에 따른 부호 적용된 금액 반환
     * - DEPOSIT: +amount
     * - WITHDRAW: -amount
     */
    public long getSignedAmount() {
        if (saveType == SaveType.WITHDRAW) {
            return -amount;
        }
        return amount;
    }
}
