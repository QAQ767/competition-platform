-- =====================================================================
-- 校园竞赛组队平台 - H2 schema (MODE=MySQL)
-- 注意: user 是 H2 保留字, 用户表命名为 users; year 是保留字, 用 award_year
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password       VARCHAR(100) NOT NULL,
    nickname       VARCHAR(50),
    avatar         VARCHAR(255),
    email          VARCHAR(100),
    college        VARCHAR(100),
    major          VARCHAR(100),
    intro          VARCHAR(500),
    project_experience VARCHAR(3000),
    preferred_roles VARCHAR(100),
    weekly_hours INT,
    role           VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    compete_status VARCHAR(20)  NOT NULL DEFAULT 'NOT_PARTICIPATE',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skill_tag (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_skill (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    skill_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS competition (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    organizer    VARCHAR(100),
    level        VARCHAR(20),
    signup_start DATETIME,
    signup_end   DATETIME,
    start_time   DATETIME,
    description  VARCHAR(1000),
    status       VARCHAR(20),
    -- 管理员维护的结构化推荐字段，旧数据允许为空
    tags              VARCHAR(300),
    eligibility_text  VARCHAR(1000),
    competition_year  INT,
    edition           VARCHAR(50),
    track             VARCHAR(100),
    source_url        VARCHAR(800),
    source_type       VARCHAR(30),
    -- 赛事实例身份键；身份不完整时为 NULL，NULL 不参与唯一约束
    identity_key      VARCHAR(80),
    UNIQUE KEY uk_competition_identity (identity_key)
);

CREATE TABLE IF NOT EXISTS team (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    competition_id BIGINT,
    captain_id     BIGINT,
    title          VARCHAR(100) NOT NULL,
    description    VARCHAR(1000),
    member_count   INT          DEFAULT 1,
    max_members    INT          DEFAULT 3,
    status         VARCHAR(20)  DEFAULT '招募中',
    deadline       DATETIME,
    skills         VARCHAR(500),
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS team_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    user_name   VARCHAR(50),
    role        VARCHAR(20) DEFAULT '队员',
    joined_time DATETIME    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS team_application (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    user_name  VARCHAR(50),
    intro      VARCHAR(500),
    status     VARCHAR(20) DEFAULT '待审批',
    invited    BOOLEAN     DEFAULT FALSE,
    apply_time DATETIME    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_recommendation (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id    BIGINT,
    user_id    BIGINT,
    match_score INT,
    reason     VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS team_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT,
    title       VARCHAR(100),
    content     VARCHAR(500),
    assignee_id BIGINT,
    phase       VARCHAR(20) NOT NULL DEFAULT '准备阶段',
    priority    VARCHAR(10) NOT NULL DEFAULT '普通',
    resource_id BIGINT,
    completion_note VARCHAR(1000),
    completed_at DATETIME,
    status      VARCHAR(20),
    deadline    DATETIME,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resource (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT,
    name        VARCHAR(100),
    url         VARCHAR(255),
    type        VARCHAR(20),
    uploader_id BIGINT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS achievement (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT,
    -- 获奖时所在队伍（队伍名快照，避免后续改名/解散影响展示）
    team_id        BIGINT,
    team_name      VARCHAR(100),
    competition_id BIGINT,
    name           VARCHAR(100),
    level          VARCHAR(20),
    award          VARCHAR(20),
    award_year     INT,
    -- 成果审核（v9.0）：发布需带证明，经管理员审核后才公开
    proof          VARCHAR(255),
    status         VARCHAR(20) DEFAULT '待审核',
    review_time    DATETIME,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_follow (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification (
    invite_id   BIGINT,
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    type        VARCHAR(20),
    content     VARCHAR(500),
    is_read     BOOLEAN DEFAULT FALSE,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operation_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT,
    action     VARCHAR(100),
    detail     VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========== 新增：队伍成员变动记录（踢出/解散） ==========
CREATE TABLE IF NOT EXISTS team_exit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT,
    team_title  VARCHAR(100),
    user_id     BIGINT,
    user_name   VARCHAR(50),
    type        VARCHAR(20) DEFAULT 'KICK',   -- KICK / DISBAND
    reason      VARCHAR(500),
    operator_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========== 新增：赛事/功能反馈 ==========
CREATE TABLE IF NOT EXISTS feedback (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    user_name   VARCHAR(50),
    type        VARCHAR(20) DEFAULT '赛事申请',  -- 赛事申请 / 功能建议 / 其他
    content     VARCHAR(1000),
    status      VARCHAR(20) DEFAULT '待处理',    -- 待处理 / 已处理
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========== 新增：聊天消息（大厅 HALL / 私聊 DM） ==========
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type   VARCHAR(10) NOT NULL,   -- HALL / DM
    user_id     BIGINT NOT NULL,
    user_name   VARCHAR(50),
    receiver_id BIGINT,                 -- DM 时的接收方
    content     VARCHAR(1000),
    is_read     BOOLEAN DEFAULT FALSE,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========== 新增：团队讨论区 ==========
CREATE TABLE IF NOT EXISTS team_discussion (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    user_name   VARCHAR(50),
    content     VARCHAR(1000),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========== 新增：训练模块 ==========
CREATE TABLE IF NOT EXISTS training_site (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    url            VARCHAR(300) NOT NULL,
    tags           VARCHAR(200),   -- 技能标签（逗号分隔，复用 skill_tag 体系）
    competition    VARCHAR(50),    -- 关联竞赛类型（ACM / CTF / 数学建模 / 蓝桥杯 / 互联网+ / 通用）
    difficulty     VARCHAR(20) DEFAULT '入门',
    description    VARCHAR(500),
    recommended    BOOLEAN DEFAULT FALSE,
    status         VARCHAR(20) DEFAULT '启用',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS training_checkin (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date)
);

CREATE TABLE IF NOT EXISTS training_favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    site_id     BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_site (user_id, site_id)
);

-- ========== 新增：赛事发现草稿（学生不可见，只有 PUBLISHED 才写入 competition） ==========
CREATE TABLE IF NOT EXISTS competition_draft (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                          VARCHAR(200),
    organizer                     VARCHAR(200),
    competition_year              INT,
    edition                       VARCHAR(50),
    track                         VARCHAR(100),
    level                         VARCHAR(20),
    description                   TEXT,
    tags                          VARCHAR(300),
    eligibility_text              VARCHAR(1000),
    signup_start                  DATETIME,
    signup_end                    DATETIME,
    start_time                    DATETIME,
    signup_end_precision          VARCHAR(10) DEFAULT 'UNKNOWN',
    date_note                     VARCHAR(500),
    date_interpretation_confirmed BOOLEAN DEFAULT FALSE,
    source_url                    VARCHAR(800),
    normalized_url                VARCHAR(800),
    page_title                    VARCHAR(300),
    source_org                    VARCHAR(200),
    published_at                  DATETIME,
    fetched_at                    DATETIME,
    source_type                   VARCHAR(30),
    extraction_mode               VARCHAR(20),
    status                        VARCHAR(20) DEFAULT 'DRAFT',
    version                       INT DEFAULT 0,
    -- 通过 MARK_READY 时的版本号；发布必须与之一致，防止审核后内容被改
    reviewed_version              INT,
    missing_fields                TEXT,
    conflicts                     TEXT,
    warnings                      TEXT,
    duplicate_candidates          TEXT,
    duplicate_acknowledged        BOOLEAN DEFAULT FALSE,
    duplicate_note                VARCHAR(500),
    stale_reason                  VARCHAR(500),
    review_note                   TEXT,
    published_competition_id      BIGINT,
    publish_key                   VARCHAR(80),
    created_by                    BIGINT,
    updated_by                    BIGINT,
    created_at                    DATETIME,
    updated_at                    DATETIME,
    UNIQUE KEY uk_draft_publish_key (publish_key),
    KEY idx_draft_status (status, updated_at)
);

CREATE TABLE IF NOT EXISTS competition_source (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id        BIGINT,
    competition_id  BIGINT,
    source_type     VARCHAR(30),
    url             VARCHAR(800),
    normalized_url  VARCHAR(800),
    page_title      VARCHAR(300),
    source_org      VARCHAR(200),
    published_at    DATETIME,
    fetched_at      DATETIME,
    fetch_status    VARCHAR(20),
    fetch_message   VARCHAR(500),
    manual          BOOLEAN DEFAULT FALSE,
    raw_text        MEDIUMTEXT,
    evidence_json   TEXT,
    created_at      DATETIME,
    KEY idx_source_draft (draft_id),
    KEY idx_source_competition (competition_id)
);

CREATE TABLE IF NOT EXISTS competition_discovery_job (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id     BIGINT,
    mode         VARCHAR(20),
    request_json TEXT,
    status       VARCHAR(20) DEFAULT 'QUEUED',
    message      VARCHAR(1000),
    result_json  TEXT,
    created_at   DATETIME,
    started_at   DATETIME,
    finished_at  DATETIME,
    KEY idx_job_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS competition_draft_review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id    BIGINT NOT NULL,
    action      VARCHAR(30),
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    operator_id BIGINT,
    note        VARCHAR(1000),
    -- 人工修正字段的前后值，供追溯
    change_summary VARCHAR(1000),
    version     INT,
    created_at  DATETIME,
    KEY idx_review_draft (draft_id)
);
