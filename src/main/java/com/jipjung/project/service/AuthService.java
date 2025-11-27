package com.jipjung.project.service;

import com.jipjung.project.controller.request.SignupRequest;
import com.jipjung.project.controller.response.SignupResponse;
import com.jipjung.project.domain.User;
import com.jipjung.project.domain.UserRole;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 이메일 중복 체크
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + request.getEmail());
        }

        // User 생성
        User user = User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .build();

        // DB 저장
        userMapper.insertUser(user);

        return new SignupResponse(user.getEmail(), user.getNickname());
    }
}
