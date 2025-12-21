package com.jipjung.project.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 법정동코드 조회 Mapper
 */
@Mapper
public interface DongcodeMapper {

    /**
     * 시/군/구 기준 법정동코드 앞 5자리(시군구코드) 조회
     */
    @Select("""
        SELECT MIN(LEFT(dong_code, 5))
        FROM dongcode
        WHERE sido_name = #{sido}
          AND gugun_name = #{sigungu}
        """)
    String findLawdCdByRegion(
            @Param("sido") String sido,
            @Param("sigungu") String sigungu
    );

    /**
     * 시/군/구 기준 법정동코드 앞 5자리(시군구코드) 조회 (시도 미지정)
     */
    @Select("""
        SELECT MIN(LEFT(dong_code, 5))
        FROM dongcode
        WHERE gugun_name = #{sigungu}
        """)
    String findLawdCdBySigungu(@Param("sigungu") String sigungu);

    /**
     * 시군구코드 + 읍면동명으로 법정동코드 조회
     */
    @Select("""
        SELECT MIN(dong_code)
        FROM dongcode
        WHERE LEFT(dong_code, 5) = #{sggCd}
          AND dong_name = #{umdNm}
        """)
    String findDongCodeBySggCdAndUmdNm(
            @Param("sggCd") String sggCd,
            @Param("umdNm") String umdNm
    );

    /**
     * 시군구코드 + 읍면동명 기준 지역 prefix (시도 + 구군)
     */
    @Select("""
        SELECT CONCAT(sido_name, ' ', gugun_name)
        FROM dongcode
        WHERE LEFT(dong_code, 5) = #{sggCd}
          AND dong_name = #{umdNm}
        LIMIT 1
        """)
    String findRegionPrefixBySggCdAndUmdNm(
            @Param("sggCd") String sggCd,
            @Param("umdNm") String umdNm
    );

    /**
     * 시군구코드 기준 지역 prefix (시도 + 구군)
     */
    @Select("""
        SELECT CONCAT(sido_name, ' ', gugun_name)
        FROM dongcode
        WHERE LEFT(dong_code, 5) = #{sggCd}
        LIMIT 1
        """)
    String findRegionPrefixBySggCd(@Param("sggCd") String sggCd);
}
