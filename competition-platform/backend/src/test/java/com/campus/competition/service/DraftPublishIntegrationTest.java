package com.campus.competition.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.campus.competition.common.BusinessException;
import com.campus.competition.dto.CompetitionReq;
import com.campus.competition.dto.DraftUpdateReq;
import com.campus.competition.entity.CompetitionDraft;
import com.campus.competition.entity.CompetitionDraftReview;
import com.campus.competition.mapper.CompetitionDraftMapper;
import com.campus.competition.mapper.CompetitionDraftReviewMapper;
import com.campus.competition.mapper.CompetitionMapper;
import com.campus.competition.mapper.CompetitionSourceMapper;
import com.campus.competition.support.CompetitionIdentity;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实 MyBatis + H2 事务下验证草稿版本、日期精度、重复检测与幂等发布。 */
@SpringJUnitConfig(DraftPublishIntegrationTest.Config.class)
class DraftPublishIntegrationTest {

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.campus.competition.mapper")
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:draft;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean CacheService cacheService() { return mock(CacheService.class); }
        @Bean CompetitionService competitionService(CompetitionMapper m, CacheService c) {
            return new CompetitionService(m, c);
        }
        @Bean CompetitionDraftService draftService(CompetitionDraftMapper d, CompetitionSourceMapper s,
                                                   CompetitionDraftReviewMapper r, CompetitionMapper c,
                                                   CompetitionService cs) {
            return new CompetitionDraftService(d, s, r, c, cs,
                    new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
                    Clock.systemDefaultZone());
        }
    }

    @Autowired CompetitionDraftService draftService;
    @Autowired CompetitionService competitionService;
    @Autowired CompetitionDraftMapper draftMapper;
    @Autowired CompetitionMapper competitionMapper;
    @Autowired CompetitionSourceMapper sourceMapper;
    @Autowired CompetitionDraftReviewMapper reviewMapper;
    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")) {{
            setSqlScriptEncoding("UTF-8");
        }}.execute(dataSource);
        for (String table : List.of("competition_draft_review", "competition_source",
                "competition_draft", "competition")) {
            jdbc.execute("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("双击发布返回同一竞赛 ID，只创建一条记录")
    void publishIsIdempotent() {
        long draftId = readyDraft("2026 测试大赛", "测试委员会", "https://example.edu.cn/a", 30, false);
        Long first = draftService.publish(draftId, versionOf(draftId), 1L);
        // 已发布草稿的重复调用是幂等读取，不要求版本号
        Long second = draftService.publish(draftId, null, 2L);
        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
        CompetitionDraft saved = draftMapper.selectById(draftId);
        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(first, saved.getPublishedCompetitionId());
        assertNotNull(saved.getPublishKey());
    }

    @Test
    @DisplayName("两个管理员并发发布最多创建一条竞赛")
    void concurrentPublishCreatesOneCompetition() throws Exception {
        long draftId = readyDraft("2026 并发杯", "并发委员会", "https://example.edu.cn/concurrent", 30, false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        final int version = versionOf(draftId);
        try {
            Future<Long> a = pool.submit(() -> { start.await(); return draftService.publish(draftId, version, 1L); });
            Future<Long> b = pool.submit(() -> { start.await(); return draftService.publish(draftId, version, 2L); });
            start.countDown();
            Long idA = a.get(15, TimeUnit.SECONDS);
            Long idB = b.get(15, TimeUnit.SECONDS);
            assertEquals(idA, idB);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("只有日期且未确认解释的草稿不能标记待发布")
    void dateOnlyDeadlineNeedsConfirmation() {
        CompetitionExtraction extraction = extraction("2026 日期精度杯", "日期委员会", null, true);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/date", "公告", "OFFICIAL", "RULE",
                "报名截止 2026年5月20日", true, 1L);
        assertNotNull(created.draft);
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> markReady(created.draft.getId(), created.draft.getVersion(), null, false));
        assertTrue(blocked.getMessage().contains("只有日期"));
        markReady(created.draft.getId(), created.draft.getVersion(), "已确认按当日截止", true);
        assertEquals("READY", draftMapper.selectById(created.draft.getId()).getStatus());
    }

    @Test
    @DisplayName("报名已截止的草稿不能作为近期赛事发布")
    void expiredDraftCannotPublish() {
        CompetitionExtraction extraction = extraction("2025 过期杯", "过期委员会", null, false);
        extraction.setSignupEnd(LocalDateTime.now().minusDays(2));
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/expired", "公告", "OFFICIAL", "RULE",
                "测试", true, 1L);
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> markReady(created.draft.getId(), created.draft.getVersion(), "测试", false));
        assertTrue(blocked.getMessage().contains("已截止"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
    }

    @Test
    @DisplayName("缺少来源的草稿不能发布")
    void draftWithoutSourceCannotPublish() {
        CompetitionDraft draft = new CompetitionDraft();
        draft.setName("无来源赛事");
        draft.setOrganizer("无来源主办方");
        draft.setSignupEnd(LocalDateTime.now().plusDays(20));
        draft.setSignupEndPrecision("DATETIME");
        draft.setStatus("READY");
        draft.setVersion(0);
        draft.setReviewedVersion(0);
        draft.setSourceType("UNKNOWN");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.insert(draft);
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.publish(draft.getId(), 0, 1L));
        assertTrue(blocked.getMessage().contains("来源"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
    }

    @Test
    @DisplayName("同一来源 URL 的赛事不被重复创建")
    void exactUrlDuplicateIsBlocked() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url) VALUES (?,?,?,?,?)",
                "已有赛事", "已有主办方", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/same");
        CompetitionExtraction extraction = extraction("已有赛事", "已有主办方", 2026, false);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/same", "公告", "OFFICIAL", "RULE", "正文", true, 1L);
        assertNull(created.draft);
        assertEquals("COMPETITION", created.duplicateType);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition_draft", Integer.class));
    }

    @Test
    @DisplayName("版本号不一致时拒绝编辑，正确版本才递增")
    void versionConflictRejectsUpdate() {
        long draftId = readyDraft("2026 版本杯", "版本委员会", "https://example.edu.cn/version", 30, false);
        CompetitionDraft draft = draftMapper.selectById(draftId);
        DraftUpdateReq stale = new DraftUpdateReq();
        stale.setVersion(draft.getVersion() - 1);
        stale.setAction("SAVE");
        assertEquals(409, assertThrows(BusinessException.class,
                () -> draftService.update(draftId, stale, 1L)).getCode());

        DraftUpdateReq valid = new DraftUpdateReq();
        valid.setVersion(draft.getVersion());
        valid.setAction("SAVE");
        valid.setName("2026 版本杯（修订）");
        var updated = draftService.update(draftId, valid, 1L);
        assertEquals("2026 版本杯（修订）", updated.getName());
        assertEquals(draft.getVersion() + 1, updated.getVersion());
    }

    // ==================== 返修回归：报告 R2 / R3 / R4 ====================

    @Test
    @DisplayName("R2：同一实例的第二份草稿在创建阶段就被拦住")
    void secondDraftForSameInstanceIsBlocked() {
        readyDraft("2026 同实例杯", "同实例委员会", "https://example.edu.cn/a", 30, false);
        CompetitionDraftService.DraftCreateResult second = draftService.createFromExtraction(
                extraction("2026 同实例杯", "同实例委员会", 2026, false),
                "https://example.edu.cn/b", "另一条公告", "OFFICIAL", "RULE", "正文", true, 1L);
        assertNull(second.draft, "同一实例不应再创建草稿");
        assertEquals("DRAFT", second.duplicateType, "重复源仍是待发布草稿");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM competition_draft", Integer.class));
    }

    @Test
    @DisplayName("R2：先建成别的赛事、再改成同一赛事，不能标记待发布")
    void renameIntoExistingInstanceIsBlocked() {
        readyDraft("2026 原赛事杯", "原赛事委员会", "https://example.edu.cn/original", 30, false);
        long other = readyDraft("2026 改名杯", "原赛事委员会", "https://example.edu.cn/other", 30, false);

        DraftUpdateReq rename = new DraftUpdateReq();
        rename.setVersion(draftMapper.selectById(other).getVersion());
        rename.setAction("SAVE");
        rename.setName("2026 原赛事杯");
        var updated = draftService.update(other, rename, 2L);
        assertTrue(updated.getDuplicateCandidates().stream()
                        .anyMatch(c -> c.getReason() != null && c.getReason().startsWith("同一实例")),
                "改名后必须重新查重并标出同一实例");
        assertFalse(updated.getDuplicateAcknowledged(), "旧差异确认必须失效");

        DraftUpdateReq ready = new DraftUpdateReq();
        ready.setVersion(draftMapper.selectById(other).getVersion());
        ready.setAction("MARK_READY");
        ready.setReviewNote("确认差异");
        ready.setDuplicateAcknowledged(true);
        ready.setDuplicateNote("测试");
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.update(other, ready, 2L));
        assertTrue(blocked.getMessage().contains("同一实例"));
    }

    @Test
    @DisplayName("R2：审核后另一条记录抢先发布同一赛事，本次发布被事务内查重阻止")
    void publishRechecksDuplicatesInsideTransaction() {
        long draftId = readyDraft("2026 抢先杯", "抢先委员会", "https://example.edu.cn/race", 30, false);

        // 模拟管理员在草稿审核后通过后台录入接口建了同一赛事。
        CompetitionReq manual = new CompetitionReq();
        manual.setName("2026 抢先杯");
        manual.setOrganizer("抢先委员会");
        manual.setYear(2026);
        manual.setSignupEnd("2026-12-31 23:59:59");
        competitionService.create(manual);

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, versionOf(draftId), 1L));
        assertEquals(409, blocked.getCode());
        assertTrue(blocked.getMessage().contains("同一赛事"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
    }

    @Test
    @DisplayName("R2：数据库身份键阻止同一实例并发入库")
    void identityKeyBlocksConcurrentDuplicate() {
        CompetitionReq first = new CompetitionReq();
        first.setName("2026 并发身份杯");
        first.setOrganizer("并发身份委员会");
        first.setYear(2026);
        first.setTrack("主赛道");
        first.setSignupEnd("2026-12-31 23:59:59");
        competitionService.create(first);

        CompetitionReq second = new CompetitionReq();
        second.setName("2026 并发身份杯");
        second.setOrganizer("并发身份委员会");
        second.setYear(2026);
        second.setTrack("主赛道");
        second.setSignupEnd("2026-11-30 23:59:59");
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> competitionService.create(second));
        assertEquals(409, conflict.getCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM competition WHERE name = '2026 并发身份杯'", Integer.class));
    }

    @Test
    @DisplayName("R3：同一 URL 的新年份允许创建草稿并提示来源复用")
    void sameUrlNewYearIsAllowed() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,competition_year) "
                        + "VALUES (?,?,?,?,?,?)",
                "2026 跨年官网杯", "跨年委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/reused", 2026);

        CompetitionExtraction newer = extraction("2026 跨年官网杯", "跨年委员会", 2027, false);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                newer, "https://example.edu.cn/reused", "2027 年新一届公告",
                "OFFICIAL", "RULE", "2027 年新一届报名通知", true, 1L);

        assertNotNull(created.draft, "新年份应按新实例创建草稿");
        assertTrue(created.duplicates.stream().anyMatch(c -> c.getReason() != null
                && c.getReason().contains("新一届")), "应提示来源复用，但允许管理员审核差异");
        assertEquals(2027, created.draft.getCompetitionYear());
    }

    @Test
    @DisplayName("R3：同一 URL 但名称写法不同，允许建草稿并提示来源复用")
    void sameUrlDifferentTitleIsSuspectNotBlocked() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,competition_year) "
                        + "VALUES (?,?,?,?,?,?)",
                "2026年第八届全国高校计算机能力挑战赛", "计算机教育研究会",
                LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/news?id=1", 2026);

        CompetitionExtraction extraction = extraction(
                "全国高校计算机能力挑战赛官网-赛事新闻", "计算机教育研究会", 2026, false);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/news?id=1", "赛事新闻",
                "OFFICIAL", "RULE", "正文", true, 1L);

        assertNotNull(created.draft, "名称写法不同不能直接判为同一实例");
        assertTrue(created.duplicates.stream().anyMatch(c -> c.getReason() != null
                        && c.getReason().contains("来源 URL")),
                "URL 相同必须作为线索提示管理员：" + created.duplicates);
    }

    @Test
    @DisplayName("R3：模型把整句写进赛道时，不能据此判成不同赛道")
    void sentenceLikeTrackDoesNotSplitIdentity() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,competition_year) "
                        + "VALUES (?,?,?,?,?,?)",
                "2026年第八届全国高校计算机能力挑战赛", "计算机教育研究会",
                LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/news?id=9", 2026);

        CompetitionExtraction extraction = extraction(
                "2026年第八届全国高校计算机能力挑战赛", "计算机教育研究会", 2026, false);
        extraction.setTrack("全国高校计算机能力挑战赛入选哈尔滨信息工程学院国家级B类学科竞赛");
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/news?id=9", "赛事新闻",
                "OFFICIAL", "RULE", "正文", true, 1L);

        assertNull(created.draft, "整句赛道不能证明这是另一条赛道");
        assertEquals("COMPETITION", created.duplicateType);
    }

    @Test
    @DisplayName("R3：届次写法不同（2026年第八届 / 第八届）仍是同一实例")
    void editionWordingDoesNotCreateNewInstance() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,"
                        + "competition_year,edition) VALUES (?,?,?,?,?,?,?)",
                "2026年第八届全国高校计算机能力挑战赛", "计算机教育研究会",
                LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/news?id=8", 2026, "第八届");

        CompetitionExtraction extraction = extraction(
                "2026年第八届全国高校计算机能力挑战赛", "计算机教育研究会", 2026, false);
        extraction.setEdition("2026年第八届");
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/news?id=8", "赛事新闻",
                "OFFICIAL", "RULE", "正文", true, 1L);

        assertNull(created.draft, "届次只是写法不同，不应判成新一届");
        assertEquals("COMPETITION", created.duplicateType);
    }

    @Test
    @DisplayName("R3：同一 URL 同一实例仍然阻止，不因为放宽 URL 判断而漏掉重复")
    void sameUrlSameInstanceStillBlocked() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,competition_year) "
                        + "VALUES (?,?,?,?,?,?)",
                "2026 同年杯", "同年委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/same-year", 2026);

        CompetitionExtraction same = extraction("2026 同年杯", "同年委员会", 2026, false);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                same, "https://example.edu.cn/same-year", "公告", "OFFICIAL", "RULE", "正文", true, 1L);
        assertNull(created.draft);
        assertEquals("COMPETITION", created.duplicateType);
    }

    // ==================== 第二轮返修回归：N3 ====================

    @Test
    @DisplayName("N3：中文数字与阿拉伯数字届次是同一实例，不允许生成第二份草稿")
    void chineseAndArabicEditionAreSameInstance() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,"
                        + "competition_year,edition,track) VALUES (?,?,?,?,?,?,?,?)",
                "同届写法杯", "验收委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/edition-a", 2026, "第八届", "软件");

        CompetitionExtraction extraction = extraction("同届写法杯", "验收委员会", 2026, false);
        extraction.setEdition("第8届");
        extraction.setTrack("软件");
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/edition-b", "公告", "OFFICIAL", "RULE", "正文", true, 1L);

        assertNull(created.draft, "第八届与第8届是同一届，不应创建第二份草稿");
        assertEquals("COMPETITION", created.duplicateType);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM competition WHERE name = '同届写法杯'", Integer.class));
    }

    @Test
    @DisplayName("N3：第八届与第九届是不同实例，可以分别创建")
    void genuinelyDifferentEditionsAllowed() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,"
                        + "competition_year,edition,track) VALUES (?,?,?,?,?,?,?,?)",
                "跨届写法杯", "验收委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/edition8", 2026, "第八届", "软件");

        CompetitionExtraction extraction = extraction("跨届写法杯", "验收委员会", 2026, false);
        extraction.setEdition("第九届");
        extraction.setTrack("软件");
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/edition9", "公告", "OFFICIAL", "RULE", "正文", true, 1L);

        assertNotNull(created.draft, "第八届与第九届是真正不同的届次，应允许创建草稿");
        assertTrue(created.duplicates.stream().anyMatch(c -> c.getReason() != null
                && c.getReason().contains("届次不同")), "应提示届次差异：" + created.duplicates);
    }

    @Test
    @DisplayName("N3：届次无法可靠归一时进入人工核验，而不是直接断定新实例")
    void unresolvableEditionNeedsManualReview() {
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,"
                        + "competition_year,edition,track) VALUES (?,?,?,?,?,?,?,?)",
                "疑难届次杯", "验收委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/ed-odd-a", 2026, "总决选", "软件");

        CompetitionExtraction extraction = extraction("疑难届次杯", "验收委员会", 2026, false);
        extraction.setEdition("预选季");
        extraction.setTrack("软件");
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, "https://example.edu.cn/ed-odd-b", "公告", "OFFICIAL", "RULE", "正文", true, 1L);

        assertNotNull(created.draft, "无法归一的届次不直接判新实例，但草稿可建、必须人工核验");
        assertTrue(created.duplicates.stream().anyMatch(c -> c.getReason() != null
                        && c.getReason().contains("人工核验")),
                "应提示需人工核验是否同一届：" + created.duplicates);
    }

    @Test
    @DisplayName("发布未发布的草稿必须携带版本号，缺失返回 400")
    void publishRequiresExpectedVersion() {
        long draftId = readyDraft("2026 版本必填杯", "版本委员会", "https://example.edu.cn/vreq", 30, false);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, null, 1L));
        assertEquals(400, missing.getCode());
        assertTrue(missing.getMessage().contains("版本号"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
        // 携带正确版本仍可发布
        assertNotNull(draftService.publish(draftId, versionOf(draftId), 1L));
    }

    // ==================== 第三轮验收回归：F2 ====================

    @Test
    @DisplayName("F2：中文数字与阿拉伯数字届次 1-99 等价（identity_key 归一一致）")
    void editionNumberEquivalence() {
        record Pair(String chinese, int number) {}
        List<Pair> pairs = List.of(
                new Pair("第八届", 8), new Pair("第九届", 9), new Pair("第十届", 10),
                new Pair("第十一届", 11), new Pair("第二十届", 20), new Pair("第二十一届", 21),
                new Pair("第九十九届", 99));
        for (Pair pair : pairs) {
            String chineseKey = CompetitionIdentity.key("归一杯", "验收委员会", 2026, pair.chinese, "软件");
            String arabicKey = CompetitionIdentity.key("归一杯", "验收委员会", 2026,
                    "第" + pair.number + "届", "软件");
            assertNotNull(chineseKey, pair.chinese + " 应能生成身份键");
            assertEquals(chineseKey, arabicKey,
                    pair.chinese + " 与 第" + pair.number + "届 应归一为同一身份键");
        }
    }

    @Test
    @DisplayName("F2：第十届与第10届走完整草稿→审核→发布，只产生一条赛事")
    void editionTenPublishesSingleInstance() {
        // 先建一份届次为“第10届”的待发布草稿（readyDraft 后再补届次/赛道）
        long draftId = readyDraft("验收十届杯", "验收委员会", "https://example.edu.cn/ten-draft", 30, false);
        jdbc.update("UPDATE competition_draft SET edition = '第10届', track = '软件' WHERE id = ?", draftId);
        int version = versionOf(draftId);

        // 已有同名、同主办方/赛道/年份、届次“第十届”的赛事
        jdbc.update("INSERT INTO competition(name,organizer,signup_end,status,source_url,"
                        + "competition_year,edition,track) VALUES (?,?,?,?,?,?,?,?)",
                "验收十届杯", "验收委员会", LocalDateTime.now().plusDays(20), "报名中",
                "https://example.edu.cn/ten-comp", 2026, "第十届", "软件");

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, version, 1L));
        assertEquals(409, blocked.getCode(), "第十届与第10届是同一届，发布应被身份键/查重拦截");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM competition WHERE name = '验收十届杯'", Integer.class));
    }

    @Test
    @DisplayName("R4：待发布草稿改内容后退回草稿，发布被拒绝，重新审核后才能发布")
    void contentEditInvalidatesReadyState() {
        long draftId = readyDraft("2026 审核失效杯", "审核委员会", "https://example.edu.cn/review", 30, false);
        int reviewedVersion = draftMapper.selectById(draftId).getVersion();

        DraftUpdateReq edit = new DraftUpdateReq();
        edit.setVersion(reviewedVersion);
        edit.setAction("SAVE");
        edit.setName("2026 审核失效杯（改）");
        var updated = draftService.update(draftId, edit, 2L);
        assertEquals("DRAFT", updated.getStatus(), "审核后改内容必须退回草稿");
        assertNull(draftMapper.selectById(draftId).getReviewedVersion(),
                "审核版本必须失效");

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, versionOf(draftId), 1L));
        assertEquals(409, blocked.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));

        CompetitionDraftReview review = firstReviewWithChanges(draftId);
        assertNotNull(review, "必须记录改动轨迹");
        assertTrue(review.getChangeSummary().contains("赛事名称"));
        assertTrue(review.getChangeSummary().contains("审核失效"));

        markReady(draftId, draftMapper.selectById(draftId).getVersion(), "重新审核", true);
        Long competitionId = draftService.publish(draftId, versionOf(draftId), 1L);
        assertNotNull(competitionId);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
    }

    @Test
    @DisplayName("R4：旧页面用过期版本发布返回 409")
    void stalePageVersionCannotPublish() {
        long draftId = readyDraft("2026 旧页面杯", "旧页面委员会", "https://example.edu.cn/stale", 30, false);
        int current = draftMapper.selectById(draftId).getVersion();

        BusinessException stale = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, current - 1, 1L));
        assertEquals(409, stale.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM competition", Integer.class));
        assertNotNull(draftService.publish(draftId, current, 1L));
    }

    @Test
    @DisplayName("R4：审核版本与当前版本不一致的草稿不能发布")
    void reviewedVersionMustMatchCurrentVersion() {
        long draftId = readyDraft("2026 审核版本杯", "审核版本委员会", "https://example.edu.cn/rv", 30, false);
        // 直接改库模拟“审核后被别处改过但状态仍是 READY”的脏数据
        jdbc.update("UPDATE competition_draft SET reviewed_version = reviewed_version - 1 WHERE id = ?", draftId);

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> draftService.publish(draftId, versionOf(draftId), 1L));
        assertEquals(409, blocked.getCode());
        assertTrue(blocked.getMessage().contains("重新标记待发布"));
    }

    private CompetitionDraftReview firstReviewWithChanges(long draftId) {
        return reviewMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompetitionDraftReview>()
                        .eq(CompetitionDraftReview::getDraftId, draftId)
                        .isNotNull(CompetitionDraftReview::getChangeSummary)
                        .orderByDesc(CompetitionDraftReview::getId))
                .stream().findFirst().orElse(null);
    }

    // ==================== 工具 ====================

    private int versionOf(long draftId) {
        Integer version = jdbc.queryForObject(
                "SELECT version FROM competition_draft WHERE id = ?", Integer.class, draftId);
        return version == null ? 0 : version;
    }

    private long readyDraft(String name, String organizer, String url, int days, boolean dateOnly) {
        CompetitionExtraction extraction = extraction(name, organizer, null, dateOnly);
        CompetitionDraftService.DraftCreateResult created = draftService.createFromExtraction(
                extraction, url, "公告", "OFFICIAL", "RULE", "测试正文", true, 1L);
        assertNotNull(created.draft, "草稿应创建成功");
        markReady(created.draft.getId(), created.draft.getVersion(), "测试来源已核对", true);
        return created.draft.getId();
    }

    private void markReady(long id, int version, String note, boolean confirmDate) {
        DraftUpdateReq req = new DraftUpdateReq();
        req.setVersion(version);
        req.setAction("MARK_READY");
        req.setReviewNote(note);
        req.setConfirmDateInterpretation(confirmDate);
        draftService.update(id, req, 1L);
    }

    private CompetitionExtraction extraction(String name, String organizer, Integer year, boolean dateOnly) {
        CompetitionExtraction extraction = new CompetitionExtraction();
        extraction.setName(name);
        extraction.setOrganizer(organizer);
        extraction.setYear(year == null ? 2026 : year);
        extraction.setLevel("校级");
        extraction.setTags(List.of("前端"));
        extraction.setSignupEnd(LocalDateTime.now().plusDays(30));
        extraction.setSignupEndPrecision(dateOnly ? "DATE" : "DATETIME");
        extraction.setMode("RULE");
        extraction.setDateNote(dateOnly ? "公告只给出日期，按当日 23:59:59 暂存" : null);
        return extraction;
    }
}
