package com.jipjung.project.repository;

import com.jipjung.project.domain.GrowthLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 성장 레벨 Mapper
 */
@Mapper
public interface GrowthLevelMapper {

    /**
     * 특정 레벨 조회
     */
    GrowthLevel findByLevel(@Param("level") int level);

    /**
     * 총 레벨 수 조회
     */
    int countAll();
}
