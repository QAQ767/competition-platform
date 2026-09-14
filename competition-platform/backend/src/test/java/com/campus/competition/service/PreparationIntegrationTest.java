package com.campus.competition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.campus.competition.common.BusinessException;
import com.campus.competition.config.PreparationSchemaUpgrade;
import com.campus.competition.dto.TaskCreateReq;
import com.campus.competition.entity.*;
import com.campus.competition.mapper.*;
import com.campus.competition.websocket.WsPusher;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 MyBatis、事务和 H2 MySQL 模式验证持久化、权限及并发。 */
@SpringJUnitConfig(PreparationIntegrationTest.Config.class)
class PreparationIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.campus.competition.mapper")
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:preparation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean WsPusher wsPusher() { return mock(WsPusher.class); }
        @Bean NotificationService notificationService(NotificationMapper n, WsPusher ws, TeamApplicationMapper a) { return new NotificationService(n, ws, a); }
        @Bean ResourceService resourceService(ResourceMapper r, TeamMemberMapper m, TeamMapper t, UserMapper u) { return new ResourceService(r, m, t, u); }
        @Bean TeamTaskService tasks(TeamTaskMapper t, TeamMapper team, UserMapper u, ResourceMapper r, CompetitionMapper c, ResourceService rs, NotificationService n, TeamMemberMapper m) { return new TeamTaskService(t, team, u, r, c, rs, n, m); }
        @Bean TeamService teams(TeamMapper t, TeamMemberMapper m, TeamApplicationMapper a, TeamExitLogMapper e, CompetitionMapper c, UserMapper u, NotificationService n) { return new TeamService(t, m, a, e, c, u, n); }
    }

    @Autowired DataSource ds;
    @Autowired TeamTaskService tasks;
    @Autowired TeamService teams;
    @Autowired NotificationService notifications;
    @Autowired TeamTaskMapper taskMapper;
    @Autowired TeamMapper teamMapper;
    @Autowired WsPusher ws;
    @Autowired PlatformTransactionManager txManager;
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        for (String table : List.of("team_task", "notification", "team_application", "team_member", "resource", "team", "competition", "users", "team_exit_log")) jdbc.execute("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id, username, password, nickname) VALUES (1, 'captain', 'test', '队长'), (2, 'member', 'test', '队员'), (3, 'other', 'test', '其他同学')");
        jdbc.update("INSERT INTO competition(id,name,start_time) VALUES (100,'测试竞赛',?)", LocalDateTime.now().plusDays(20));
        jdbc.update("INSERT INTO team(id,title,captain_id,competition_id,member_count,max_members) VALUES (10,'测试队伍',1,100,2,3), (20,'其他队伍',3,100,1,3)");
        jdbc.update("INSERT INTO team_member(team_id,user_id,user_name,role) VALUES (10,1,'队长','队长'), (10,2,'队员','队员'), (20,3,'其他同学','队长')");
        jdbc.update("INSERT INTO resource(id,team_id,name,url,uploader_id) VALUES (30,10,'本队资料','/uploads/test.pdf',1), (40,20,'其他队资料','/uploads/other.pdf',3)");
        reset(ws);
    }

    TaskCreateReq request() {
        TaskCreateReq req = new TaskCreateReq();
        req.setTitle("完成模拟训练"); req.setContent("在规定时间内完成一套赛题");
        req.setPhase("模拟冲刺"); req.setPriority("重要"); req.setAssigneeId(2L);
        req.setResourceId(30L); req.setDeadline("2030-10-01 18:00:00");
        return req;
    }

    @Test void taskLifecyclePersistsAssignmentResourceAndReview() {
        TeamTask task = tasks.create(10L, 1L, request());
        var view = tasks.list(10L, 2L).get(0);
        assertEquals("本队资料", view.getResourceName());
        assertEquals("队员", view.getAssigneeName());
        assertEquals("模拟冲刺", view.getPhase());
        assertEquals(1, notifications.list(2L).size());
        tasks.updateStatus(task.getId(), 2L, "进行中", null);
        tasks.updateStatus(task.getId(), 2L, "已完成", "下次注意时间分配");
        TeamTask completed = taskMapper.selectById(task.getId());
        assertNotNull(completed.getCompletedAt());
        assertEquals("下次注意时间分配", completed.getCompletionNote());
        assertEquals(1, notifications.list(1L).size());
        tasks.updateStatus(task.getId(), 2L, "已完成", "重复点击");
        assertEquals(1, notifications.list(1L).size());
        tasks.updateStatus(task.getId(), 1L, "待完成", null);
        assertNull(taskMapper.selectById(task.getId()).getCompletedAt());
        assertNull(taskMapper.selectById(task.getId()).getCompletionNote());
        TaskCreateReq edit = request(); edit.setAssigneeId(null); edit.setResourceId(null); edit.setDeadline(null);
        tasks.update(task.getId(), 1L, edit);
        TeamTask saved = taskMapper.selectById(task.getId());
        assertNull(saved.getAssigneeId()); assertNull(saved.getResourceId()); assertNull(saved.getDeadline());
    }

    @Test void rejectsCrossTeamAccessAndFormerAssignee() {
        TaskCreateReq req = request(); req.setAssigneeId(3L);
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        req.setAssigneeId(2L); req.setResourceId(40L);
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        assertThrows(BusinessException.class, () -> tasks.create(10L, 2L, request()));
        assertThrows(BusinessException.class, () -> tasks.list(10L, 3L));
        TeamTask task = tasks.create(10L, 1L, request());
        assertThrows(BusinessException.class, () -> tasks.update(task.getId(), 2L, request()));
        assertThrows(BusinessException.class, () -> tasks.delete(task.getId(), 2L));
        jdbc.update("DELETE FROM team_member WHERE team_id=10 AND user_id=2");
        assertThrows(BusinessException.class, () -> tasks.updateStatus(task.getId(), 2L, "已完成", "已离队"));
        assertEquals("待完成", taskMapper.selectById(task.getId()).getStatus());
    }

    @Test void rejectsInvalidInputsWithoutWritingTasks() {
        TaskCreateReq req = request(); req.setDeadline("2026-02-30 12:00:00");
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        req.setDeadline(null); req.setPhase("无效阶段");
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        req.setPhase("准备阶段"); req.setPriority("未知");
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        req.setPriority("普通"); req.setTitle(" ");
        assertThrows(BusinessException.class, () -> tasks.create(10L, 1L, req));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM team_task", Integer.class));
    }

    @Test void generatesPlanFromCompetitionDateWithoutOverwritingTasks() {
        List<TeamTask> plan = tasks.generatePlan(10L, 1L, null);
        assertEquals(4, plan.size());
        assertEquals(List.of("准备阶段", "专项训练", "模拟冲刺", "作品提交"), plan.stream().map(TeamTask::getPhase).toList());
        assertTrue(plan.get(0).getDeadline().isAfter(LocalDateTime.now()));
        assertTrue(plan.get(3).getDeadline().isAfter(plan.get(2).getDeadline()));
        assertThrows(BusinessException.class, () -> tasks.generatePlan(10L, 1L, "2030-01-01"));
        assertEquals(4, tasks.list(10L, 1L).size());
    }

    @Test void requiresFuturePlanTargetAndCaptain() {
        assertThrows(BusinessException.class, () -> tasks.generatePlan(10L, 2L, null));
        assertThrows(BusinessException.class, () -> tasks.generatePlan(10L, 1L, "2020-01-01"));
        jdbc.update("UPDATE competition SET start_time=NULL WHERE id=100");
        assertThrows(BusinessException.class, () -> tasks.generatePlan(10L, 1L, null));
        assertEquals(4, tasks.generatePlan(10L, 1L, "2030-01-01").size());
    }

    @Test void simultaneousPlanGenerationOnlyCreatesOnePlan() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> work = () -> { start.await(); try { tasks.generatePlan(10L, 1L, null); return true; } catch (BusinessException e) { return false; } };
        try {
            Future<Boolean> a = pool.submit(work), b = pool.submit(work); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(4, tasks.list(10L, 1L).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void invitationsRemainActionableAfterReadAndOnlyMatchingNotificationIsHandled() {
        teams.invite(10L, 3L, 1L);
        teams.invite(20L, 2L, 3L);
        Notification first = notifications.list(3L).get(0);
        assertNotNull(first.getInviteId());
        notifications.clearAll(3L);
        assertEquals("待审批", notifications.list(3L).get(0).getInviteStatus());
        teams.acceptInvite(first.getInviteId(), 3L);
        assertEquals("已通过", notifications.list(3L).get(0).getInviteStatus());
        assertTrue(notifications.list(3L).get(0).getIsRead());
        assertEquals("待审批", notifications.list(2L).get(0).getInviteStatus());
        assertFalse(notifications.list(2L).get(0).getIsRead());
        assertThrows(BusinessException.class, () -> teams.acceptInvite(first.getInviteId(), 3L));
    }

    @Test void sameUsersSecondInvitationIsNotMarkedRead() {
        jdbc.update("INSERT INTO team(id,title,captain_id,competition_id,member_count,max_members) VALUES (30,'第二队伍',1,100,1,3)");
        teams.invite(10L, 3L, 1L); teams.invite(30L, 3L, 1L);
        List<Notification> notices = notifications.list(3L);
        Long handledId = notices.get(1).getInviteId();
        teams.rejectInvite(handledId, 3L);
        assertEquals(1, notifications.list(3L).stream().filter(n -> !n.getIsRead()).count());
        assertEquals("待审批", notifications.list(3L).stream().filter(n -> !n.getInviteId().equals(handledId)).findFirst().orElseThrow().getInviteStatus());
    }

    @Test void notificationWebSocketPushOnlyHappensAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> { notifications.send(2L, "TASK", "测试回滚"); verifyNoInteractions(ws); status.setRollbackOnly(); return null; });
        verifyNoInteractions(ws);
        assertTrue(notifications.list(2L).isEmpty());
        tx.execute(status -> { notifications.send(2L, "TASK", "测试提交"); verifyNoInteractions(ws); return null; });
        verify(ws).pushToUser(eq(2L), any());
    }

    @Test void acceptingSameInvitationConcurrentlyOnlyAddsOneMember() throws Exception {
        teams.invite(10L, 3L, 1L);
        Long inviteId = notifications.list(3L).get(0).getInviteId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> work = () -> { start.await(); try { teams.acceptInvite(inviteId, 3L); return true; } catch (BusinessException e) { return false; } };
        try {
            Future<Boolean> a = pool.submit(work), b = pool.submit(work); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=10 AND user_id=3", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT member_count FROM team WHERE id=10", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void deadlineScanClosesAllExpiredStatusesAtBoundaryAndIsIdempotent() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE team SET deadline=?,status='招募中' WHERE id=10", now);
        jdbc.update("UPDATE team SET deadline=?,status='已满员' WHERE id=20", now.minusSeconds(1));
        jdbc.update("INSERT INTO team(id,title,captain_id,status,deadline) VALUES(30,'备赛队伍',1,'备赛中',?),(40,'未来队伍',1,'招募中',?),(50,'无截止时间',1,'招募中',NULL)", now.minusDays(1), now.plusSeconds(1));
        assertEquals(3, teamMapper.closeExpiredTeams(now));
        assertEquals(0, teamMapper.closeExpiredTeams(now));
        for (long id : List.of(10L,20L,30L)) assertEquals("已结束", teamMapper.selectById(id).getStatus());
        assertEquals("招募中", teamMapper.selectById(40L).getStatus());
        assertEquals("招募中", teamMapper.selectById(50L).getStatus());
        assertEquals(2, teamMapper.selectById(10L).getMemberCount());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=10", Integer.class));
    }

    @Test void expiredTeamRejectsEveryAdmissionPathBeforeNextScheduledScan() {
        teams.invite(10L, 3L, 1L);
        Long invitation = notifications.list(3L).get(0).getInviteId();
        jdbc.update("UPDATE team SET deadline=? WHERE id=10", LocalDateTime.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> teams.apply(10L,3L,null));
        assertThrows(BusinessException.class, () -> teams.invite(10L,3L,1L));
        assertThrows(BusinessException.class, () -> teams.approve(10L,invitation,1L));
        assertThrows(BusinessException.class, () -> teams.acceptInvite(invitation,3L));
        assertEquals(2, teamMapper.selectById(10L).getMemberCount());
        assertEquals("待审批", notifications.list(3L).get(0).getInviteStatus());
    }

    @Test void endedTeamWithoutDeadlineCannotRecruitAndExpiredFullTeamCannotReopen() {
        jdbc.update("UPDATE team SET status='已结束' WHERE id=20");
        assertThrows(BusinessException.class, () -> teams.apply(20L,2L,null));
        jdbc.update("UPDATE team SET status='已满员',deadline=? WHERE id=10", LocalDateTime.now().minusMinutes(1));
        teams.leave(10L,2L);
        assertEquals("已结束", teamMapper.selectById(10L).getStatus());
        assertEquals(1, teamMapper.selectById(10L).getMemberCount());
    }

    @Test void scheduledEntryClosesExpiredTeamsAndKeepsEndedStatusAfterEdits() throws Exception {
        jdbc.update("UPDATE team SET deadline=? WHERE id=10", LocalDateTime.now().minusSeconds(1));
        new TeamDeadlineScheduler(teamMapper).checkDeadlines();
        teams.updateDescription(10L,"保留资料",1L);
        teams.kick(10L,2L,"测试移除",1L);
        assertEquals("已结束", teamMapper.selectById(10L).getStatus());
        assertEquals("保留资料", teamMapper.selectById(10L).getDescription());
        var schedule = TeamDeadlineScheduler.class.getMethod("checkDeadlines").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals(30000, schedule.fixedRate());
    }

    @Test void upgradesLegacySchemaIdempotentlyAndKeepsData() throws Exception {
        DriverManagerDataSource legacy = new DriverManagerDataSource("jdbc:h2:mem:legacy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate old = new JdbcTemplate(legacy);
        old.execute("CREATE TABLE IF NOT EXISTS team_task(id BIGINT PRIMARY KEY, title VARCHAR(100))");
        old.execute("CREATE TABLE IF NOT EXISTS notification(id BIGINT PRIMARY KEY, content VARCHAR(500))");
        old.update("INSERT INTO team_task(id,title) VALUES(1,'保留旧任务')");
        PreparationSchemaUpgrade upgrade = new PreparationSchemaUpgrade(legacy);
        upgrade.run(); upgrade.run();
        assertEquals("保留旧任务", old.queryForObject("SELECT title FROM team_task WHERE id=1", String.class));
        assertEquals("准备阶段", old.queryForObject("SELECT phase FROM team_task WHERE id=1", String.class));
        assertEquals("普通", old.queryForObject("SELECT priority FROM team_task WHERE id=1", String.class));
        assertNull(old.queryForObject("SELECT resource_id FROM team_task WHERE id=1", Long.class));
        old.update("INSERT INTO notification(id,content,invite_id) VALUES(1,'测试邀请',123)");
        assertEquals(123L, old.queryForObject("SELECT invite_id FROM notification WHERE id=1", Long.class));
    }
}
