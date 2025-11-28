package com.jipjung.project.repository;

import com.jipjung.project.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findByEmail(@Param("email") String email);

    int insertUser(User user);

    boolean existsByEmail(@Param("email") String email);
}
