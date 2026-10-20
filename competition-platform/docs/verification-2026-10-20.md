# 2026-10-20 实现与验收记录

工作目录：`D:\Moon\competition-platform`  
验收环境：Windows、JDK 17.0.5、MySQL 8.0（本机 `competition_platform`）、Redis（本机 `.tools/redis`）、Ollama 0.40.2（`deepseek-r1:7b`）、Node 22.22.2。

## 1. 基线

- Git 根目录 `D:\Moon`，当前分支 `main`，本地 HEAD `e4c261d`（未联网核对远端）。
- 保留未提交的 `README.md`、`DataSeeder.java` 修改，以及 `DemoScenarioSeeder`、`demo/scenarios-20261014.json`、`docs/demo-scenarios.md` 等未跟踪演示材料；未执行 `git add`/`commit`/`push`。
- 未修改、未提交 `D:\Moon\材料`、`GPT材料`、`workbuddy材料`、毕业设计模板等个人材料。
- 数据库升级前 26 用户、15 赛事、20 队伍；升级与多次重启后仍为 26 用户、20 队伍，赛事增加 1 条本次真实验收发布的赛事（16 条）。

## 2. 自动化测试

| 命令 | 结果 |
|---|---|
| `cd backend && mvn -o test` | **150 tests, 0 failures, 0 errors, 0 skipped**（第四轮返修后） |
| `cd frontend && node --test tests/*.test.js` | **17 tests, 17 pass, 0 fail** |
| `cd frontend && npm run build` | 成功；仅有既存的 chunk >500 kB 警告 |

> 历史基线：第一轮交付时为后端 103 / 前端 14；第一轮返修（R1–R6）后后端 132；第二轮返修（N1–N3）后后端 141。测试数量仅记录自动化断言，真实搜索/Redis/浏览器等外部依赖验收仍需单独完成。

第三轮返修新增测试覆盖：

- `CompetitionExtractionServiceTest`（+6 项，F1）：中午12点不能被 DATE 抹成 23:59:59、仅日期不能补 00:00、点/点半/时/冒号/上午下午正确保留、错误时刻统一拒绝、无法解析的时刻线索进入待核对、仅日期+DATE 仍走人工确认。
- `DraftPublishIntegrationTest`（+2 项，F2）：中文数字与阿拉伯数字届次 1–99 等价（identity_key 归一一致）、第十届/第10届走完整草稿→审核→发布被拦截且只保留一条。

第三轮边界探针（`D:\Moon\.tools\acceptance-review-20261020-round3`）复跑：

```text
noon_dian_as_date=null        unsupported_midnight=null
edition10_key_equal=true      edition10_second_draft=false
edition10_published_rows=1
```

两处 F1/F2 边界缺陷的复现用例现在全部为预期值（原先分别是 23:59:59 / 00:00 / false / true / 2）。

第四轮返修（F3）：修正 `点N分` 的显式分钟被外层捕获组误判为“半”的确定性错误——`parseTimeToken` 改为先解析 group(6)（显式分钟数字）再判 group(5)（半）。新增 `explicitMinutesParsedCorrectly` 遍历 0–59 分验证 `12点N分`≡`12:N`、非 30 分钟不接受 12:30。

第四轮探针（`D:\Moon\.tools\acceptance-review-20261020-round4`）：

```text
correct_12_15=2026-11-25T12:15       incorrect_12_30=null
correct_minutes_rejected_out_of_60=0 wrong_half_hour_accepted_out_of_59=0
```

（原先分别是 null / 12:30 / 59 / 59。）



新增测试覆盖：

- `RecommendationEligibilityServiceTest`（13 项）：截止时刻边界、未来报名、缺日期、排除方向、专业限制、`weeklyHours=0`、队伍满员/队长/已申请/截止、明确时间要求、互补评分、不参赛用户、多截止冲突、仅有日期、对话时间覆盖档案、排除方向过滤队伍、月份日期不推算。
- `StudentRecommendationAiTest`（5 项）：合法输出、假 ID、重复 ID、缺失候选、越界分数、残缺 JSON、超长理由、超长建议角色清洗、空候选。
- `AdvicePreferenceServiceTest`（8 项）：明确事实提取、后续覆盖、否定句移除方向、不编造、0 小时追问、模型非法/合法输出、假字段拒绝。
- `AdviceSessionServiceTest`（6 项）：会话归属、版本冲突、长度限制、清空、Redis 503。
- `UrlSafetyPolicyTest`（4 项）：私网/元数据/IPv6 本地地址、公网地址、危险 URL/端口/凭据。
- `CompetitionExtractionServiceTest`（2 项）：正文补齐证据、无完整日期证据时拒绝模型推算。
- `DraftPublishIntegrationTest`（7 项，H2 真实 MyBatis/事务）：幂等发布、并发发布、日期精度确认、过期草稿、缺来源、同 URL 重复、版本冲突。
- `AdminSecurityIntegrationTest`（4 项，SpringBootTest + MockMvc）：学生访问管理员接口、学生绕过草稿写竞赛、管理员写竞赛/状态、学生创建会话。

## 3. 真实联调状态

### 3.1 数据库

| 项 | 实际结果 |
|---|---|
| MySQL | 本机 8.0，库 `competition_platform` 可连接 |
| 迁移 | `schema.sql` 新建 4 张表；`PreparationSchemaUpgrade` 为旧 `competition` 补 7 个可选字段；启动多次幂等 |
| 数据 | 26 用户、20 队伍未丢失；演示数据未重复导入、日期未被顺延 |
| 新表 | `competition_draft`、`competition_source`、`competition_discovery_job`、`competition_draft_review` 均存在 |

### 3.2 Redis

- 本机 `redis-server` 启动后 `PING → PONG`。
- 创建会话、发消息、读会话、清空均实际通过。
- 未做“停掉 Redis 后前端提示”的人工截图；单元测试覆盖 503，接口文案为“推荐会话存储暂不可用（Redis 未连接）”。

### 3.3 模型（Ollama）

- 模型：`deepseek-r1:7b`，7.6B，Q4_K_M，约 4.4 GB，位于 `D:\models`；Ollama 0.40.2 在 `127.0.0.1:11434`。
- 模型模式成功：student1 推荐返回 `mode=LLM`（约 58 s），5 竞赛/5 队伍并带 `insight`；对话“每周 3 小时 + 排除建模”经 LLM 提取，再用 LLM 推荐时理由明确写“每周可投入 3 小时”，建模队伍被过滤。
- 模型质量不稳定：同一 7B 模型在另几次调用中出现重复 ID（如 14/15）或 120 s 超时，服务端按设计整批回退规则结果，`fallbackReason` 明确写出原因；未把半份模型结果混入页面。
- 未验证更大模型或模型服务重启后的稳定性；当前结论只适用于本机这次运行。

### 3.4 搜索与网页抓取

- Brave 未配置：`GET /api/admin/competition-discovery/status` 返回 `searchConfigured=false`，文案“未启用联网搜索（discovery.search.provider=none）”。**平台联网搜索未完成验收**。
- URL 抓取真实验收：抓取 `https://www.ncccu.org.cn/index/Cms/content.html?id=427`（2026 年第八届全国高校计算机能力挑战赛官网新闻页），取回正文 973 字，LLM 提取名称/主办方/年份/届次/级别/截止日期；来源表记录 `FETCHED`、抓取时间与 URL；逐字段证据可见（名称、主办方、报名截止等）。
- 本机代理把公网域名解析到 `198.18.0.0/15`。默认 SSRF 策略拒绝该段（首次作业返回 `URL_PRIVATE_BLOCKED`）；本地联调显式设置 `DISCOVERY_ALLOW_FAKE_IP=true` 后才成功抓取。生产必须保持 `false`。
- 人工文本导入真实验收：粘贴教育部 2026 创新大赛通知正文，草稿来源为 `MANUAL_TEXT`，`sourceUrl=null`、`fetchStatus=PROVIDED`；正文只有“9月25日12时”时，报名截止留空并列入缺失，没有采用模型推算的 2026-09-25。
- 无 PDF 抓取能力；遇到 PDF 会提示粘贴文本。

### 3.5 发布与重复

- 真实草稿 2（全国高校计算机能力挑战赛）经管理员确认日期解释、填写审核备注、修正字段后发布为竞赛 **17**；连续两次调用发布均返回 **17**，数据库只有一条该赛事。
- 再次用同一 URL 启动作业，返回 `FAILED / 已存在相同赛事`，`duplicateType=COMPETITION, duplicateId=17`，未新建草稿。
- 发布时使用行锁 + `publish_key` 唯一键；H2 并发测试两个线程发布同一草稿，均返回同一 ID，只创建一条竞赛。

## 4. A01–A20 验收对照

| 编号 | 结果 | 证据/说明 |
|---|---|---|
| A01 | **通过（规则降级）** | student3（前端/UI）返回真实竞赛与队伍，理由引用技能/队伍需求，详情与队伍链接可用；该次模型因重复 ID 回退规则，页面明确标识“规则匹配” |
| A02 | **通过** | student99 空档案返回 7 个缺失项、`confidence=EXPLORE`、低分探索结果，未编造经历；LLM 模式成功 |
| A03 | **通过** | 对话把每周 6 小时改为 3 小时；会话摘要 `weeklyHours=3`、来源 `DIALOGUE`；后续 LLM 推荐理由写“每周可投入 3 小时”；`users` 表未被改写 |
| A04 | **通过** | 单测覆盖“排除硬件”；真实验收“不想参加建模了”后建模方向竞赛/队伍均被过滤，模型未加回 |
| A05 | **通过** | student7 `weeklyHours=0`、`ONLY_VIEW`：摘要显示“未确定”，推荐风险提示“时间适配未计分”和“不修改公开状态”；student5 `NOT_PARTICIPATE` 同样得到提示 |
| A06 | **通过** | 固定 Clock 单测覆盖截止时刻、过期、即将报名、缺日期；页面类别为“可报名/可提前准备/需确认”，已截止赛事只写“报名已截止”，未说“赛事已完赛” |
| A07 | **通过** | student7 已有待审批申请，推荐把它放入 `appliedTeams` 而非重复推荐；满员队伍申请返回“队伍当前状态为「已满员」，暂不招募”；单测覆盖队长/成员/截止 |
| A08 | **通过** | H2 并发入队/发布测试；真实满员队伍申请在服务端被拒绝；推荐返回前重新查询成员数并更新剩余名额 |
| A09 | **通过** | 单测覆盖假 ID、重复 ID、缺候选、越界分数、残缺 JSON；真实 7B 输出重复 ID 时整批回退并显示“模型输出未通过校验（竞赛 重复 ID 15）”，未显示伪造数据 |
| A10 | **通过** | 真实模型超时返回 `mode=RULE` 与“模型调用失败：模型调用超时”，页面结束加载并显示完整规则结果；无无限转圈 |
| A11 | **通过** | 学生访问 `/api/admin/competition-drafts` → HTTP 403；学生 `POST /api/competitions` → HTTP 200 + `code=403`；管理员正常；SpringBootTest 覆盖 |
| A12 | **部分通过** | URL 导入真实抓取 + 发布已验收；**平台联网搜索 API 未配置，未做真实搜索验收** |
| A13 | **通过（限流未测）** | 密钥缺失时状态接口明确显示未启用；URL/文本导入可用；未配置/网页失败均保留真实错误码。Brave 限流未实测 |
| A14 | **通过** | 单测覆盖多个报名截止不择一、仅月日不推算；真实文本草稿只识别 2026-08-10 开始，9 月 25 日截止留空并列入缺失 |
| A15 | **通过** | 真实日期型截止按 2026-11-25 23:59:59 保存，标记 `DATE`；未确认时发布阻塞；确认后才 READY/PUBLISHED |
| A16 | **部分通过** | 同 URL 重复真实验收阻止；名称+主办方+年份+赛道与疑似重复逻辑有实现，但未准备“同赛事新旧届次/多赛道”的真实样本做端到端验收 |
| A17 | **通过** | 真实双击发布返回同一竞赛 17；H2 两线程并发发布只创建一条；版本冲突返回 409 |
| A18 | **部分通过** | SSRF 单测覆盖回环/私网/链路本地/元数据/IPv6；真实抓取首次被 `URL_PRIVATE_BLOCKED` 拦截；提示注入未做专门红队测试，代码只把正文当数据并禁止模型写库 |
| A19 | **通过** | 旧库补字段、新表创建、多次重启；26 用户/20 队伍未丢失，演示数据未重复；`PreparationSchemaUpgrade` 幂等 |
| A20 | **通过** | 原队长推荐、学习问答、申请/审批、队伍任务等既有测试全部通过；推荐后仍需学生提交、队长审批，无自动入队 |

## 5. 修改文件清单

### 后端新增

- 配置：`ClockConfig`、`AdviceProperties`、`DiscoveryProperties`、`DiscoveryExecutorConfig`、`DiscoveryJobRecovery`
- 实体/映射：`CompetitionDraft`、`CompetitionSource`、`CompetitionDiscoveryJob`、`CompetitionDraftReview` 及 4 个 Mapper
- 推荐领域：`advice/AdvicePreferences`、`AdviceSession`、`AdviceMessage`、`PreferenceChange`、`ParticipantProfile`、`RulePreferenceExtractor`
- 服务：`RecommendationEligibilityService`、`StudentRecommendationService`、`StudentRecommendationAi`、`AdviceSessionStore`、`RedisAdviceSessionStore`、`AdviceSessionService`、`AdvicePreferenceService`、`TeamRecruitmentGuard`
- 抓取/搜索：`UrlSafetyPolicy`、`PublicHttpFetcher`、`WebContentFetcher`、`FetchedPage`、`FetchException`、`HtmlTextExtractor`、`CompetitionSearchProvider`、`BraveSearchProvider`、`SearchHit`、`SearchException`
- 提取/草稿：`CompetitionExtraction`、`RuleCompetitionExtractor`、`CompetitionExtractionService`、`CompetitionDraftService`、`CompetitionDiscoveryService`
- 接口：`AdviceController`、`AdminCompetitionDiscoveryController`、`AdminCompetitionDraftController`
- DTO/VO：`AdviceMessageReq`、`AdvicePreferencesReq`、`AdviceRecommendationReq`、`DraftUpdateReq`、`DiscoveryJobReq`；`AdviceProfileSummaryVO`、`CompetitionRecommendationVO`、`TeamRecommendationVO`、`StudentRecommendationVO`、`AppliedTeamVO`、`AdviceSessionVO`、`AdviceMessageResultVO`、`CompetitionDraftVO`、`DuplicateCandidateVO`、`DiscoveryJobVO`、`DiscoveryStatusVO`

### 后端修改

`Competition.java`、`CompetitionReq.java`、`CompetitionService.java`、`CompetitionController.java`、`TeamService.java`、`LlmService.java`、`LlmProperties.java`、`SecurityConfig.java`、`PreparationSchemaUpgrade.java`、`schema.sql`、`application.yml`。

### 前端

- 新增：`views/Advice.vue`、`views/CompetitionDetail.vue`、`utils/advice.js`、`utils/draft.js`
- 修改：`views/Admin.vue`（赛事发现 Tab、草稿审核、发布）、`App.vue`（导航）、`Home.vue`（快捷入口）、`TeamSquare.vue`（入口）、`TeamDetail.vue`（`?apply=1` 打开申请）、`router/index.js`
- 测试：`tests/advice-utils.test.js`、`tests/draft-utils.test.js`
- 截图：`docs/screenshots/advice-results-viewport.png`、`advice-recommendation.png`、`admin-discovery.png`

### 文档

`docs/student-advice.md`、`docs/competition-discovery.md`、本文件。

## 6. 兼容性与部署

- 旧数据：新字段全部可空，不强制回填；现有 `competition` 列表/筛选/组队逻辑不变。
- 旧接口：`GET /api/ai/recommend?teamId=` 保留队长权限与行为；学习助手未改。
- 新增写权限：`POST/DELETE/PUT /api/competitions` 需要 `ROLE_ADMIN`；原普通用户写入口已关闭。
- 数据库：新库由 `schema.sql` 建表；旧库启动时由 `PreparationSchemaUpgrade` 补字段。升级前请备份。
- Docker：未部署、未重启容器。若部署，需要同步镜像/构建，并把 `SEARCH_PROVIDER`、`BRAVE_SEARCH_API_KEY`、`LLM_MODEL`、`LLM_TIMEOUT_SECONDS` 等环境变量传入后端容器；生产不要设置 `DISCOVERY_ALLOW_FAKE_IP=true`。
- 本机后端以 `--server.port=8080` 运行过验收；前端 Vite `5173` 仅用于截图。

## 7. 剩余问题

1. **Brave 搜索未验收**：没有 API key；需配置后核验至少 3 条公告的来源与日期，并把结果补进本表。
2. **7B 模型不稳定**：会重复 ID 或超时，已按设计回退规则；如需更稳定的模型模式，建议换更大模型或增加“只输出排序”的二次校验，不应放宽 ID 校验。
3. **证据定位质量**：模型省略证据时，补齐逻辑可能把页面标题作为名称/级别的证据；管理员仍需核对原文。真实抓取的草稿已提供正文折叠区。
4. **真实 Redis CAS 未独立联调**：会话版本冲突逻辑有源码与回归测试，第二轮未独立重跑真实 Redis Lua 交错场景。
5. **提示注入**：未做专门红队测试；当前仅通过“正文视为数据 + 模型无写库工具 + 服务端权限”降低风险。
6. **UI 自动化**：仅用 Chrome CDP 截图验证渲染与真实数据，没有浏览器自动化回归测试；推荐/发现的 UI 交互主要靠人工路径与构建验证。
7. **已发布竞赛编辑**：新增 `PUT /api/competitions/{id}` 管理员接口用于更正，管理端暂未提供独立编辑表单；本次通过接口修正了验收赛事。
8. **未部署**：GitHub/Docker 均未推送或更新；工作区保留所有未提交演示数据与 README 修改。
9. **届次身份键迁移**：届次归一改为数值形式后，启动时 `PreparationSchemaUpgrade` 会重算全部 `identity_key`；历史重复数据只记录告警，不删除、不静默取舍，需人工核对日志中的冲突条目。

