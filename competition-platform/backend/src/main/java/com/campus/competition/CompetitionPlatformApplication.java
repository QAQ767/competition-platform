package com.campus.competition;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园竞赛组队平台后端 Demo 启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.campus.competition.mapper")
public class CompetitionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompetitionPlatformApplication.class, args);
    }
}
