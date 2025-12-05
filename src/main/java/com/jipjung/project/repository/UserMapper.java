package com.jipjung.project.repository;

import com.jipjung.project.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 사용자 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * ID로 활성 사용자 조회 (is_deleted=false)
     */
    User findById(@Param("userId") Long userId);

    Optional<User> findByEmail(@Param("email") String email);

    int insertUser(User user);

    boolean existsByEmail(@Param("email") String email);
}

