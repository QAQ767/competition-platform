package com.campus.competition.service;

import com.campus.competition.seed.DemoScenarioSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DemoScenarioSeederTest {
    JdbcTemplate jdbc;
    DemoScenarioSeeder seeder;
    final LocalDate date = LocalDate.of(2026, 10, 14);

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")) {{
            setSqlScriptEncoding("UTF-8");
        }}.execute(ds);
        jdbc = new JdbcTemplate(ds);
        seeder = new DemoScenarioSeeder(jdbc, new DataSourceTransactionManager(ds), true);
        jdbc.update("INSERT INTO users(username,password,nickname,intro,weekly_hours) VALUES ('student1','original','小林','用户自己的介绍',0),('real_user','secret','真实用户',null,null)");
    }

    @Test void fillsOnlyBlankDemoFieldsAndPreservesEditsOnRepeatedImport() {
        seeder.populate(date);
        assertEquals("用户自己的介绍", jdbc.queryForObject("SELECT intro FROM users WHERE username='student1'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT weekly_hours FROM users WHERE username='student1'", Integer.class));
        assertEquals("original", jdbc.queryForObject("SELECT password FROM users WHERE username='student1'", String.class));
        assertTrue(jdbc.queryForObject("SELECT project_experience FROM users WHERE username='student1'", String.class).contains("Spring Boot"));
        assertNull(jdbc.queryForObject("SELECT project_experience FROM users WHERE username='real_user'", String.class));
        jdbc.update("UPDATE users SET project_experience='后续自行编辑' WHERE username='demo26_01'");
        Object deadline = jdbc.queryForObject("SELECT MIN(deadline) FROM team", Object.class);
        seeder.populate(date.plusDays(100));
        assertEquals(18, count("users"));
        assertEquals(10, count("competition"));
        assertEquals(14, count("team"));
        assertEquals(1, count("demo_seed_batch"));
        assertEquals(deadline, jdbc.queryForObject("SELECT MIN(deadline) FROM team", Object.class));
        assertEquals("后续自行编辑", jdbc.queryForObject("SELECT project_experience FROM users WHERE username='demo26_01'", String.class));
    }

    @Test void teamMembershipDatesProfilesAndCandidatePoolAreCoherent() {
        seeder.populate(date);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM team t WHERE member_count<>(SELECT COUNT(*) FROM team_member m WHERE m.team_id=t.id) OR NOT EXISTS (SELECT 1 FROM team_member m WHERE m.team_id=t.id AND m.user_id=t.captain_id AND m.role='队长')", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM team t JOIN competition c ON t.competition_id=c.id WHERE t.deadline>c.signup_end OR c.signup_start>=c.signup_end OR c.signup_end>=c.start_time", Integer.class));
        assertEquals(13, jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE status='招募中'", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM competition WHERE status='即将报名'", Integer.class));
        assertEquals(16, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username LIKE 'demo26_%' AND project_experience IS NOT NULL AND preferred_roles IS NOT NULL AND weekly_hours IS NOT NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM team t WHERE t.status='招募中' AND NOT EXISTS (SELECT 1 FROM users u WHERE u.compete_status IN ('WANT_INVITE','ACCEPT_INVITE') AND NOT EXISTS (SELECT 1 FROM team_member m WHERE m.team_id=t.id AND m.user_id=u.id))", Integer.class));
    }

    @Test void collisionRollsBackEntireBatchAndCanBeRetried() {
        jdbc.update("INSERT INTO competition(name) VALUES ('校园软件应用开发赛（2026秋季演示）')");
        assertThrows(IllegalStateException.class, () -> seeder.populate(date));
        assertEquals(2, count("users"));
        assertEquals(0, count("demo_seed_batch"));
        assertNull(jdbc.queryForObject("SELECT project_experience FROM users WHERE username='student1'", String.class));
        jdbc.update("DELETE FROM competition");
        seeder.populate(date);
        assertEquals(14, count("team"));
    }

    @Test void renamedLegacyAccountIsNotEnriched() {
        jdbc.update("UPDATE users SET nickname='自定义用户' WHERE username='student1'");
        seeder.populate(date);
        assertNull(jdbc.queryForObject("SELECT project_experience FROM users WHERE username='student1'", String.class));
    }

    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
