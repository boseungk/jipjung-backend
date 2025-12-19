package com.jipjung.project.support;

import com.jipjung.project.domain.User;
import com.jipjung.project.domain.UserRole;
import com.jipjung.project.service.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * WithMockCustomUser 어노테이션을 처리하여 SecurityContext에 CustomUserDetails를 설정
 */
public class WithMockCustomUserSecurityContextFactory 
        implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        User user = User.builder()
                .id(annotation.userId())
                .email(annotation.email())
                .nickname(annotation.nickname())
                .password("encodedPassword")
                .role(UserRole.valueOf(annotation.role()))
                .isActive(true)
                .isDeleted(false)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        
        context.setAuthentication(auth);
        return context;
    }
}
