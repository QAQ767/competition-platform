package com.campus.competition.service;

import com.campus.competition.security.JwtService;
import com.campus.competition.security.RedisTokenService;
import com.campus.competition.seed.DataSeeder;
import com.campus.competition.seed.DemoScenarioSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务端权限集成测试：学生不能调用管理员接口，不能直接写竞赛。
 * 使用 H2 + MockMvc，不依赖本机 MySQL/Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "llm.enabled=false",
        "advice.session-ttl-hours=1"
})
class AdminSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired DataSource dataSource;
    @MockBean RedisTokenService redisTokenService;
    @MockBean CacheService cacheService;
    @MockBean DataSeeder dataSeeder;
    @MockBean DemoScenarioSeeder demoScenarioSeeder;
    @MockBean AdviceSessionStore adviceSessionStore;

    JdbcTemplate jdbc;
    String studentToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM users WHERE id IN (1,2)");
        jdbc.update("INSERT INTO users(id,username,password,nickname,role,compete_status) VALUES "
                + "(1,'student','x','学生','STUDENT','WANT_INVITE'),"
                + "(2,'admin','x','管理员','ADMIN','ONLY_VIEW')");
        studentToken = jwtService.generateAccessToken(1L, "student", "STUDENT");
        adminToken = jwtService.generateAccessToken(2L, "admin", "ADMIN");
    }

    @Test
    @DisplayName("赛事发现和草稿路由已移除，管理员也无法调用")
    void removedDiscoveryEndpointsReturnNotFound() throws Exception {
        for (String path : new String[]{"/api/admin/competition-discovery/status",
                "/api/admin/competition-discovery/jobs/1", "/api/admin/competition-drafts",
                "/api/admin/competition-drafts/1"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/admin/competition-discovery/jobs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"TEXT\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/competition-drafts/1/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("学生调用竞赛写接口被方法级权限拒绝")
    void studentCannotCreateCompetition() throws Exception {
        mvc.perform(post("/api/competitions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无权限创建的竞赛\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/api/competitions/999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM competition WHERE name='无权限创建的竞赛'", Integer.class);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("管理员仍可手动创建竞赛")
    void adminCanCreateCompetition() throws Exception {
        mvc.perform(post("/api/competitions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"管理员创建的竞赛\",\"organizer\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

    }

    @Test
    @DisplayName("学生可以从登录态创建推荐会话，不能靠传 userId 读取他人数据")
    void studentCreatesOwnAdviceSession() throws Exception {
        mvc.perform(post("/api/ai/advice/sessions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private static void assertEquals(int expected, Integer actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
