package com.jipjung.project.service;

import com.jipjung.project.domain.User;
import com.jipjung.project.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));

        if (Boolean.TRUE.equals(user.getIsDeleted()) || Boolean.FALSE.equals(user.getIsActive())) {
            throw new UsernameNotFoundException("비활성화된 계정입니다: " + email);
        }

        return new CustomUserDetails(user);
    }
}
