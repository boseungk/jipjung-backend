package com.jipjung.project.repository;

import com.jipjung.project.domain.SavingsHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 저축 내역 Mapper
 */
@Mapper
public interface SavingsHistoryMapper {

    /**
     * 특정 시점 이전까지의 저축 합계 조회 (KST 기준 시점을 UTC로 변환한 값 전달)
     * - DEPOSIT: +, WITHDRAW: -
     * - 차트 윈도우 시작 잔액 계산용
     */
    Long sumBeforeDate(@Param("dreamHomeId") Long dreamHomeId, @Param("beforeDateTime") LocalDateTime beforeDateTime);

    /**
     * 날짜 범위 내 저축 내역 조회
     * - created_at 기준 ASC 정렬
     * - 범위 파라미터는 KST 기준을 UTC로 변환한 값 전달
     */
    List<SavingsHistory> findByDreamHomeIdAndDateRange(
            @Param("dreamHomeId") Long dreamHomeId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
