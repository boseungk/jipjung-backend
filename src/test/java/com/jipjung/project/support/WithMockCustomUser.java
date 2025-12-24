package com.jipjung.project.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 테스트에서 CustomUserDetails를 SecurityContext에 주입하기 위한 커스텀 어노테이션
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory.class)
public @interface WithMockCustomUser {
    long userId() default 1L;
    String email() default "test@example.com";
    String nickname() default "테스트유저";
    String role() default "USER";
}
