package com.jipjung.project.repository;

import com.jipjung.project.domain.ThemeAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 테마 에셋 Mapper
 */
@Mapper
public interface ThemeAssetMapper {

    /**
     * 테마 ID와 레벨로 에셋 조회
     * - house_theme.is_active = true, is_deleted = false 조건 포함
     */
    ThemeAsset findByThemeAndLevel(@Param("themeId") int themeId, @Param("level") int level);
}
