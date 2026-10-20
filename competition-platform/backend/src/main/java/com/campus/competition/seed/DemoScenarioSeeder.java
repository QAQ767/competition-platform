package com.campus.competition.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Explicit, additive demo upgrade. The batch marker and all data changes commit together. */
@Component
@Order(100)
public class DemoScenarioSeeder implements CommandLineRunner {
    static final String BATCH = "collaboration-demo-20261014";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    public DemoScenarioSeeder(JdbcTemplate jdbc, PlatformTransactionManager manager,
                              @Value("${demo.scenarios.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.enabled = enabled;
    }

    @Override public void run(String... args) {
        if (enabled) populate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    public void populate(LocalDate date) {
        JsonNode data;
        try (InputStream input = new ClassPathResource("demo/scenarios-20261014.json").getInputStream()) {
            data = new ObjectMapper().readTree(input);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取演示场景数据", e);
        }
        // DDL is outside the transaction because MySQL implicitly commits DDL.
        jdbc.execute("CREATE TABLE IF NOT EXISTS demo_seed_batch (batch_key VARCHAR(100) PRIMARY KEY, applied_at DATETIME NOT NULL)");
        transaction.executeWithoutResult(status -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM demo_seed_batch WHERE batch_key=?", Integer.class, BATCH) > 0) return;
            jdbc.update("INSERT INTO demo_seed_batch(batch_key,applied_at) VALUES (?,?)", BATCH, date.atStartOfDay());
            String password = new BCryptPasswordEncoder().encode("123456");
            Map<String, Long> users = new HashMap<>();
            for (JsonNode p : data.path("profiles")) {
                String username = p.path("username").asText();
                List<Map<String, Object>> existing = jdbc.queryForList("SELECT id,nickname FROM users WHERE username=?", username);
                boolean legacy = username.startsWith("student");
                if (existing.size() > 1) throw new IllegalStateException("重复用户名：" + username);
                if (!existing.isEmpty()) {
                    // New account namespace must not collide with a pre-existing user.
                    if (!legacy) throw new IllegalStateException("演示账号已被占用：" + username);
                    if (!p.path("nickname").asText().equals(existing.get(0).get("nickname"))) continue;
                    long id = ((Number) existing.get(0).get("id")).longValue();
                    fillBlank(id, "intro", p.path("intro").asText());
                    fillBlank(id, "project_experience", p.path("experience").asText());
                    fillBlank(id, "preferred_roles", p.path("roles").asText());
                    jdbc.update("UPDATE users SET weekly_hours=? WHERE id=? AND weekly_hours IS NULL", p.path("hours").asInt(), id);
                    users.put(username, id);
                } else if (!legacy) {
                    jdbc.update("INSERT INTO users(username,password,nickname,college,major,intro,project_experience,preferred_roles,weekly_hours,role,compete_status,created_at) VALUES (?,?,?,?,?,?,?,?,?,'STUDENT',?,?)",
                            username, password, p.path("nickname").asText(), p.path("college").asText(), p.path("major").asText(),
                            p.path("intro").asText(), p.path("experience").asText(), p.path("roles").asText(), p.path("hours").asInt(),
                            p.path("status").asText(), date.atTime(9, 0));
                    long id = id("users", "username", username);
                    users.put(username, id);
                    for (JsonNode skill : p.path("skills")) {
                        String name = skill.asText();
                        if (jdbc.queryForObject("SELECT COUNT(*) FROM skill_tag WHERE name=?", Integer.class, name) == 0)
                            jdbc.update("INSERT INTO skill_tag(name) VALUES (?)", name);
                        long skillId = jdbc.queryForObject("SELECT MIN(id) FROM skill_tag WHERE name=?", Long.class, name);
                        jdbc.update("INSERT INTO user_skill(user_id,skill_id) VALUES (?,?)", id, skillId);
                    }
                }
            }
            Map<String, Long> competitions = new HashMap<>();
            Map<String, LocalDateTime> closing = new HashMap<>();
            for (JsonNode c : data.path("competitions")) {
                String name = c.path("name").asText();
                assertUnused("competition", "name", name);
                LocalDateTime start = date.plusDays(c.path("openDay").asInt()).atTime(9, 0);
                LocalDateTime end = date.plusDays(c.path("closeDay").asInt()).atTime(23, 59, 59);
                String state = start.isAfter(date.atTime(12, 0)) ? "即将报名" :
                        end.isBefore(date.plusDays(7).atTime(12, 0)) ? "报名即将截止" : "报名中";
                jdbc.update("INSERT INTO competition(name,organizer,level,signup_start,signup_end,start_time,description,status) VALUES (?,'校竞赛实践中心（演示）','校级',?,?,?,?,?)",
                        name, start, end, date.plusDays(c.path("eventDay").asInt()).atTime(9, 0),
                        "【虚构演示赛事，非官方赛程】" + c.path("description").asText(), state);
                competitions.put(c.path("key").asText(), id("competition", "name", name));
                closing.put(c.path("key").asText(), end);
            }
            for (JsonNode t : data.path("teams")) {
                String title = t.path("title").asText();
                assertUnused("team", "title", title);
                List<Long> members = new ArrayList<>();
                for (JsonNode member : t.path("members")) {
                    Long user = users.get(member.asText());
                    if (user == null || members.contains(user)) throw new IllegalStateException("无效演示队员：" + member.asText());
                    members.add(user);
                }
                int capacity = t.path("capacity").asInt();
                if (members.isEmpty() || members.size() > capacity) throw new IllegalStateException("无效队伍人数：" + title);
                LocalDateTime deadline = date.plusDays(t.path("deadlineDay").asInt()).atTime(20, 0);
                String key = t.path("competition").asText();
                if (!competitions.containsKey(key) || deadline.isAfter(closing.get(key))) throw new IllegalStateException("无效队伍赛程：" + title);
                jdbc.update("INSERT INTO team(competition_id,captain_id,title,description,member_count,max_members,status,deadline,skills,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        competitions.get(key), members.get(0), title, "【演示队伍】" + t.path("description").asText(),
                        members.size(), capacity, members.size() == capacity ? "已满员" : "招募中", deadline,
                        t.path("skills").asText(), date.atTime(9, 0));
                long teamId = id("team", "title", title);
                for (int i = 0; i < members.size(); i++) {
                    long member = members.get(i);
                    jdbc.update("INSERT INTO team_member(team_id,user_id,user_name,role,joined_time) VALUES (?,?,?,?,?)", teamId, member,
                            jdbc.queryForObject("SELECT nickname FROM users WHERE id=?", String.class, member), i == 0 ? "队长" : "队员", date.atTime(9, 0));
                }
            }
        });
    }

    private void fillBlank(long id, String column, String value) {
        jdbc.update("UPDATE users SET " + column + "=? WHERE id=? AND (" + column + " IS NULL OR TRIM(" + column + ")='')", value, id);
    }

    private long id(String table, String column, String value) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    private void assertUnused(String table, String column, String value) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class, value) > 0)
            throw new IllegalStateException("演示数据名称已被占用：" + value);
    }
}
