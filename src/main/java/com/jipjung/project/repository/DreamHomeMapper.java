package com.jipjung.project.repository;

import com.jipjung.project.domain.DreamHome;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 드림홈 Mapper
 */
@Mapper
public interface DreamHomeMapper {

    /**
     * 사용자의 활성 드림홈 조회
     * - status = 'ACTIVE', is_deleted = false
     * - 아파트 정보 JOIN 포함
     */
    DreamHome findActiveByUserId(@Param("userId") Long userId);
}
