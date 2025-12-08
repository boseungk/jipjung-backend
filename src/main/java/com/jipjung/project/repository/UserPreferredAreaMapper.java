package com.jipjung.project.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 선호 지역 Mapper
 */
@Mapper
public interface UserPreferredAreaMapper {

    /**
     * 사용자의 선호 지역 일괄 삽입
     */
    int insertAll(@Param("userId") Long userId, @Param("areas") List<String> areas);

    /**
     * 사용자의 선호 지역 전체 삭제
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 사용자의 선호 지역 목록 조회
     */
    List<String> findByUserId(@Param("userId") Long userId);
}
