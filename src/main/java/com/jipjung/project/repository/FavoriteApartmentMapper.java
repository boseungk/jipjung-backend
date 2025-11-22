package com.jipjung.project.repository;

import com.jipjung.project.domain.FavoriteApartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FavoriteApartmentMapper {

    int insert(FavoriteApartment favorite);

    List<FavoriteApartment> findByUserId(@Param("userId") Long userId);

    Optional<FavoriteApartment> findById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    boolean existsByUserIdAndApartmentId(@Param("userId") Long userId, @Param("apartmentTransactionId") Long apartmentTransactionId);
}
