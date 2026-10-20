package com.campus.competition.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 统一使用北京时间。业务规则通过注入 Clock 获得“当前时间”，
 * 测试可以传入固定 Clock，避免依赖机器当天日期。
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock businessClock() {
        return Clock.system(ZONE);
    }
}
