package com.jipjung.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 시간/타임존 관련 설정
 */
@Configuration
public class TimeConfig {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE_KST);
    }
}

