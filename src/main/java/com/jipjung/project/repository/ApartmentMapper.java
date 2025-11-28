package com.jipjung.project.repository;

import com.jipjung.project.controller.dto.request.ApartmentSearchRequest;
import com.jipjung.project.domain.ApartmentTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ApartmentMapper {

    List<ApartmentTransaction> findAll(@Param("request") ApartmentSearchRequest request);

    Optional<ApartmentTransaction> findById(@Param("id") Long id);

    int count(@Param("request") ApartmentSearchRequest request);
}
