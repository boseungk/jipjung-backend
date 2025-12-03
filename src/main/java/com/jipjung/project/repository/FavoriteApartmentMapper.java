package com.jipjung.project.repository;

import com.jipjung.project.domain.FavoriteApartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 관심 아파트 Mapper
 * schema.sql의 favorite_apartment 테이블 접근
 */
@Mapper
public interface FavoriteApartmentMapper {

    /**
     * 관심 아파트 등록
     * @param favorite 관심 아파트 정보
     * @return 등록된 행 수
     */
    int insert(FavoriteApartment favorite);

    /**
     * 사용자별 관심 아파트 목록 조회
     * @param userId 사용자 ID
     * @return 관심 아파트 목록 (아파트 정보 포함)
     */
    List<FavoriteApartment> findByUserId(@Param("userId") Long userId);

    /**
     * 관심 아파트 상세 조회
     * @param id 관심 아파트 ID
     * @return 관심 아파트 (아파트 정보 포함)
     */
    Optional<FavoriteApartment> findById(@Param("id") Long id);

    /**
     * 관심 아파트 삭제
     * @param id 관심 아파트 ID
     * @return 삭제된 행 수
     */
    int deleteById(@Param("id") Long id);

    /**
     * 관심 아파트 중복 체크
     * @param userId 사용자 ID
     * @param aptSeq 아파트 코드
     * @return 존재 여부
     */
    boolean existsByUserIdAndAptSeq(@Param("userId") Long userId, @Param("aptSeq") String aptSeq);
}
