-- 2026-09-16 演示数据调整。执行前备份数据库；仅清理没有关联的两条过期竞赛。
-- 新活动均标明“演示”，日期仅供平台功能展示，不代表真实赛事官方日程。
START TRANSACTION;

DELETE FROM competition
WHERE (id = 6 AND name = '天梯杯' OR id = 7 AND name = 'ACM')
  AND signup_end <= NOW()
  AND NOT EXISTS (SELECT 1 FROM team WHERE team.competition_id = competition.id)
  AND NOT EXISTS (SELECT 1 FROM achievement WHERE achievement.competition_id = competition.id);

INSERT INTO competition (name, organizer, level, signup_start, signup_end, start_time, description, status)
SELECT '校园程序设计秋季赛（演示）', '校竞赛中心', '校级',
       '2026-09-14 09:00:00', '2026-09-20 23:59:59', '2026-09-26 09:00:00',
       '平台演示用校内算法训练赛，面向程序设计与数据结构练习。',
       CASE WHEN NOW() >= '2026-09-20 23:59:59' THEN '已结束'
            WHEN NOW() < '2026-09-14 09:00:00' OR '2026-09-20 23:59:59' < NOW() + INTERVAL 7 DAY THEN '即将开始'
            ELSE '报名中' END
WHERE NOT EXISTS (SELECT 1 FROM competition WHERE name = '校园程序设计秋季赛（演示）');

INSERT INTO competition (name, organizer, level, signup_start, signup_end, start_time, description, status)
SELECT '校园数据分析挑战赛（演示）', '校竞赛中心', '校级',
       '2026-09-15 09:00:00', '2026-10-02 23:59:59', '2026-10-11 09:00:00',
       '平台演示用校内数据分析训练赛，练习清洗、建模与可视化。',
       CASE WHEN NOW() >= '2026-10-02 23:59:59' THEN '已结束'
            WHEN NOW() < '2026-09-15 09:00:00' OR '2026-10-02 23:59:59' < NOW() + INTERVAL 7 DAY THEN '即将开始'
            ELSE '报名中' END
WHERE NOT EXISTS (SELECT 1 FROM competition WHERE name = '校园数据分析挑战赛（演示）');

INSERT INTO competition (name, organizer, level, signup_start, signup_end, start_time, description, status)
SELECT '校园网络安全闯关赛（演示）', '校竞赛中心', '校级',
       '2026-09-15 09:00:00', '2026-10-13 23:59:59', '2026-10-21 09:00:00',
       '平台演示用校内网络安全训练赛，覆盖基础安全知识与攻防实践。',
       CASE WHEN NOW() >= '2026-10-13 23:59:59' THEN '已结束'
            WHEN NOW() < '2026-09-15 09:00:00' OR '2026-10-13 23:59:59' < NOW() + INTERVAL 7 DAY THEN '即将开始'
            ELSE '报名中' END
WHERE NOT EXISTS (SELECT 1 FROM competition WHERE name = '校园网络安全闯关赛（演示）');

INSERT INTO competition (name, organizer, level, signup_start, signup_end, start_time, description, status)
SELECT '校园创新项目路演赛（演示）', '校竞赛中心', '校级',
       '2026-09-15 09:00:00', '2026-10-26 23:59:59', '2026-11-03 09:00:00',
       '平台演示用校内创新项目展示活动，练习项目表达与答辩。',
       CASE WHEN NOW() >= '2026-10-26 23:59:59' THEN '已结束'
            WHEN NOW() < '2026-09-15 09:00:00' OR '2026-10-26 23:59:59' < NOW() + INTERVAL 7 DAY THEN '即将开始'
            ELSE '报名中' END
WHERE NOT EXISTS (SELECT 1 FROM competition WHERE name = '校园创新项目路演赛（演示）');

COMMIT;
