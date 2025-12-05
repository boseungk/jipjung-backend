package com.jipjung.project.repository;

import com.jipjung.project.domain.StreakHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 스트릭 기록 Mapper
 */
@Mapper
public interface StreakHistoryMapper {

    /**
     * 주간 스트릭 기록 조회 (월~일)
     */
    List<StreakHistory> findByUserIdAndWeek(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd
    );

    /**
     * 특정 날짜 스트릭 존재 여부 확인
     */
    boolean existsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}
