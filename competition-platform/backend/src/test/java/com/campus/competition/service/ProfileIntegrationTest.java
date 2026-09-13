package com.campus.competition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.campus.competition.common.GlobalExceptionHandler;
import com.campus.competition.common.UserContext;
import com.campus.competition.controller.UserController;
import com.campus.competition.controller.UserProfileController;
import com.campus.competition.dto.ProfileReq;
import com.campus.competition.mapper.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实持久化与 MVC 参数校验；登录上下文由测试提供，不依赖外部数据库。 */
@SpringJUnitConfig(ProfileIntegrationTest.Config.class)
class ProfileIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.campus.competition.mapper")
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:profile;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean UserService users(UserMapper u, UserSkillMapper s, SkillTagMapper tags, TeamMapper t, TeamMemberMapper m, TeamApplicationMapper a, CompetitionMapper c) { return new UserService(u, s, tags, t, m, a, c); }
        @Bean FollowService follows(FollowMapper f, UserMapper u, UserSkillMapper s, SkillTagMapper t) { return new FollowService(f, u, s, t); }
    }

    @Autowired DataSource ds;
    @Autowired UserService users;
    @Autowired FollowService follows;
    @Autowired UserMapper userMapper;
    @Autowired UserSkillMapper userSkillMapper;
    @Autowired SkillTagMapper skillTagMapper;
    JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        jdbc.execute("DELETE FROM user_follow");
        jdbc.execute("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,password,nickname,email,avatar,college,major,intro) VALUES (1,'me','secret','我的昵称','old@example.com','/old.png','计算机学院','软件工程','个人简介'),(2,'peer','secret','队友','peer@example.com',null,null,null,null),(3,'fan','secret','粉丝',null,null,null,null,null)");
        mvc = MockMvcBuilders.standaloneSetup(new UserController(users),
                new UserProfileController(userMapper, userSkillMapper, skillTagMapper, follows, mock(AchievementService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.setUserId(1L);
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void emailUpdateTrimsPersistsAndReturnsCompleteProfile() throws Exception {
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  new@example.com  \",\"id\":2,\"role\":\"ADMIN\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("我的昵称"))
                .andExpect(jsonPath("$.data.avatar").value("/old.png"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
        assertEquals("new@example.com", userMapper.selectById(1).getEmail());
        assertEquals("peer@example.com", userMapper.selectById(2).getEmail());
        assertEquals("secret", userMapper.selectById(1).getPassword());
    }

    @Test void avatarOnlyUpdatePreservesAllOtherFieldsInDatabaseAndResponse() {
        ProfileReq req = new ProfileReq();
        req.setAvatar("/new.png");
        var result = users.updateProfile(1L, req);
        assertEquals("old@example.com", result.getEmail());
        assertEquals("计算机学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals("个人简介", result.getIntro());
        assertEquals("/new.png", userMapper.selectById(1).getAvatar());
        assertEquals("计算机学院", userMapper.selectById(1).getCollege());
    }

    @Test void invalidEmailIsRejectedWithoutChangingProfile() throws Exception {
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"nickname\":\"不应保存\"}"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请输入有效的邮箱地址"));
        assertEquals("old@example.com", userMapper.selectById(1).getEmail());
        assertEquals("我的昵称", userMapper.selectById(1).getNickname());
    }

    @Test void oversizedEmailIsRejectedBeforeDatabaseWrite() throws Exception {
        String email = "a".repeat(60) + "@" + "b".repeat(40) + ".com";
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("邮箱不能超过 100 个字符"));
        assertEquals("old@example.com", userMapper.selectById(1).getEmail());
    }

    @Test void blankEmailClearsAndOmittedFieldsAreUnchanged() throws Exception {
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"  \"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.email").value(""));
        mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.email").value(""));
        assertEquals("", userMapper.selectById(1).getEmail());
        assertEquals("我的昵称", userMapper.selectById(1).getNickname());
    }

    @Test void connectionListsUseLoggedInUserAndCorrectDirection() throws Exception {
        follows.follow(1L, 2L);
        follows.follow(3L, 1L);
        follows.follow(2L, 3L);
        mvc.perform(get("/api/users/me/following").param("userId", "2"))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(2));
        mvc.perform(get("/api/users/me/followers").param("userId", "2"))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(3));
    }

    @Test void followBackAndUnfollowPersistWithoutRemovingFollower() throws Exception {
        follows.follow(3L, 1L);
        mvc.perform(post("/api/users/3/follow")).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/users/3/follow")).andExpect(jsonPath("$.code").value(200));
        assertEquals(1, follows.followingCount(1L));
        assertTrue(follows.isFollowing(1L, 3L));
        mvc.perform(delete("/api/users/3/follow")).andExpect(jsonPath("$.code").value(200));
        assertEquals(0, follows.followingCount(1L));
        assertEquals(1, follows.followerCount(1L));
        assertEquals(3L, follows.followerUsers(1L).get(0).getId());
    }

    @Test void emptyListsAndInvalidTargetsAreHandled() throws Exception {
        mvc.perform(get("/api/users/me/followers")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/users/me/following")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/users/1/follow")).andExpect(jsonPath("$.message").value("不能关注自己"));
        mvc.perform(post("/api/users/999/follow")).andExpect(jsonPath("$.message").value("用户不存在"));
        assertEquals(0, follows.followingCount(1L));
    }
}
