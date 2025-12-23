package com.jipjung.project.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 스트릭 인정 활동 유형
 * <p>
 * 각 활동은 하루에 1회만 EXP를 지급하며, 스트릭 유지 조건으로 인정됩니다.
 * 
 * @see com.jipjung.project.service.StreakService
 */
@Getter
@RequiredArgsConstructor
public enum ActivityType {
    
    /** 대시보드 접속 - 매일 첫 접속 시 */
    DASHBOARD("대시보드 접속", 10),
    
    /** AI 지출 분석 완료 */
    AI_ANALYSIS("AI 지출 분석", 30),
    
    /** AI 판결 완료 */
    AI_JUDGMENT("AI 판결", 20),
    
    /** 저축 기록 (첫 입금만) */
    SAVINGS("저축", 20);

    private final String label;
    private final int baseExp;
    
    /**
     * 모든 활동 유형의 기본 EXP 합계
     * <p>
     * 일일 EXP 상한 계산 시 참조용
     * 
     * @return 모든 활동 기본 EXP 합계 (80)
     */
    public static int getTotalBaseExp() {
        int total = 0;
        for (ActivityType type : values()) {
            total += type.baseExp;
        }
        return total;
    }
}
