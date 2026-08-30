package com.campus.competition;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园竞赛组队平台后端 Demo 启动类
 */
@SpringBootApplication
@MapperScan("com.campus.competition.mapper")
public class CompetitionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompetitionPlatformApplication.class, args);
    }
}
