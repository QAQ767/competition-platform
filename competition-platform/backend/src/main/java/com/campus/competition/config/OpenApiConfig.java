package com.campus.competition.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档配置：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("竞赛组队平台 API")
                .description("校园竞赛组队平台后端接口文档（认证：登录后取 accessToken，请求头加 Authorization: Bearer <token>）")
                .version("v1.0.0"));
    }
}
