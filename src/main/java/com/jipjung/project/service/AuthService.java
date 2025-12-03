package com.jipjung.project.service;

import com.jipjung.project.config.exception.DuplicateEmailException;
import com.jipjung.project.config.exception.ErrorCode;
import com.jipjung.project.controller.dto.request.SignupRequest;
import com.jipjung.project.controller.dto.response.SignupResponse;
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
        validateSameEmail(request);

        User user = createUser(request);
        userMapper.insertUser(user);

        return new SignupResponse(user.getEmail(), user.getNickname());
    }

    private void validateSameEmail(SignupRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw new DuplicateEmailException(ErrorCode.DUPLICATE_EMAIL.getMessage());
        }
    }

    private User createUser(SignupRequest request) {
        return User.builder()
                .email(request.email())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build();
    }
}
