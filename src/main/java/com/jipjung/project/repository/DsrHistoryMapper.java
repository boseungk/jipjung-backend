package com.jipjung.project.repository;

import com.jipjung.project.domain.DsrCalculationHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * DSR 계산 이력 Mapper
 */
@Mapper
public interface DsrHistoryMapper {

    /**
     * 이력 저장
     */
    @Insert("""
        INSERT INTO dsr_calculation_history
            (user_id, input_json, result_json, dsr_mode, max_loan_amount)
        VALUES
            (#{userId}, #{inputJson}, #{resultJson}, #{dsrMode}, #{maxLoanAmount})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DsrCalculationHistory history);

    /**
     * 최근 N건의 이력 조회
     */
    @Select("""
        SELECT * FROM dsr_calculation_history
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "inputJson", column = "input_json"),
        @Result(property = "resultJson", column = "result_json"),
        @Result(property = "dsrMode", column = "dsr_mode"),
        @Result(property = "maxLoanAmount", column = "max_loan_amount"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<DsrCalculationHistory> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 최신 PRO 이력 조회 (대시보드용)
     */
    @Select("""
        SELECT * FROM dsr_calculation_history
        WHERE user_id = #{userId} AND dsr_mode = 'PRO'
        ORDER BY created_at DESC
        LIMIT 1
    """)
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "inputJson", column = "input_json"),
        @Result(property = "resultJson", column = "result_json"),
        @Result(property = "dsrMode", column = "dsr_mode"),
        @Result(property = "maxLoanAmount", column = "max_loan_amount"),
        @Result(property = "createdAt", column = "created_at")
    })
    DsrCalculationHistory findLatestProByUserId(@Param("userId") Long userId);
}
