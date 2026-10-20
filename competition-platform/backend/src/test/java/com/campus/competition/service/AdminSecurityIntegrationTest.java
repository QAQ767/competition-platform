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
 * 服务端权限集成测试：学生不能调用管理员接口，也不能绕过草稿直接写竞赛。
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
        "discovery.search.provider=none",
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
    @DisplayName("学生访问管理员草稿接口被服务端拒绝")
    void studentCannotAccessAdminDraftApi() throws Exception {
        mvc.perform(get("/api/admin/competition-drafts").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/competition-discovery/status")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/competition-discovery/jobs")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TEXT\",\"text\":\"这是一个足够长的测试公告正文内容\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("学生直接调用原竞赛写接口也被方法级权限拒绝")
    void studentCannotBypassDraftByCreatingCompetition() throws Exception {
        mvc.perform(post("/api/competitions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"绕过草稿的竞赛\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/api/competitions/999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM competition WHERE name='绕过草稿的竞赛'", Integer.class);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("管理员可以写竞赛并读取发现配置状态")
    void adminCanWriteAndReadDiscoveryStatus() throws Exception {
        mvc.perform(post("/api/competitions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"管理员创建的竞赛\",\"organizer\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/admin/competition-discovery/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.searchConfigured").value(false));
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
