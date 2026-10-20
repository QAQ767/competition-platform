# 学生参赛推荐：实现与使用说明

更新日期：2026-10-20  
入口：登录后访问 `/advice`（侧边栏“参赛推荐”，首页与管理端也有入口）。

## 1. 它做什么

学生可以：

1. 直接按个人档案生成推荐，分别得到最多 5 场竞赛和 5 支可申请队伍；
2. 用一句话补充自身情况（技能、时间、方向、排除项等），查看并编辑“本次需求摘要”，再重新推荐；
3. 在卡片上看到真实理由、待确认条件、建议角色和下一步，再自行决定是否申请。

推荐不会自动报名、自动入队或自动修改公开档案；申请仍由学生提交、队长审批。

## 2. 数据来源与边界

| 数据 | 来源 | 说明 |
|---|---|---|
| 专业、学院、简介、项目经历、期望角色、每周时间、参赛意向 | `users` 表 | `weeklyHours=0` 或空值表示“未确定”，不是“完全没有时间” |
| 技能 | `user_skill` + `skill_tag` | 只使用用户自己填写的标签 |
| 竞赛 | `competition` 表 | **只推荐已发布的平台赛事**；网页搜索结果不会混入 |
| 队伍 | `team`、`team_member`、`team_application` | 实际成员记录优先于 `member_count` 字段 |
| 对话需求 | Redis 会话 | 24 小时 TTL，按用户隔离，不默认写回公开档案 |

竞赛的 `tags`、`eligibilityText`、年份/届次/赛道是 2026-10-20 新增的可选字段。旧数据允许为空；为空时只能用名称/简介关键词做弱匹配，并在卡片上标注“名称关键词推断”。

## 3. 接口

所有接口都从登录态取 `userId`，客户端传入其他用户 ID 无效。统一返回 `{code,message,data}`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/advice/sessions` | 创建会话，返回档案摘要、缺失项和空需求 |
| GET | `/api/ai/advice/sessions/{id}` | 读取会话；仅会话所有者 |
| POST | `/api/ai/advice/sessions/{id}/messages` | 发送一句话，返回答复、结构化变更、追问和更新后的摘要 |
| PUT | `/api/ai/advice/sessions/{id}/preferences` | 保存/编辑摘要，必须带 `version` |
| POST | `/api/ai/advice/sessions/{id}/recommendations` | 按当前摘要推荐，可带 `windowDays` |
| DELETE | `/api/ai/advice/sessions/{id}` | 清空会话；之后恢复按档案推荐 |
| POST | `/api/ai/advice/profile-recommendations` | 不依赖 Redis，仅按档案推荐（P1 独立入口） |

### 3.1 发送消息

```json
{ "message": "我只能投入 3 小时，不想参加建模了。" }
```

返回：

```json
{
  "sessionId": "…",
  "version": 1,
  "reply": "已按你的原话记录：每周时间 设为 3；排除项 增加 建模。",
  "mode": "LLM",
  "fallbackReason": null,
  "changes": [
    { "field": "weeklyHours", "op": "SET", "values": ["3"], "display": "每周时间 设为：3" }
  ],
  "questions": ["…"],
  "preferences": {
    "weeklyHours": 3,
    "excludedKeywords": ["建模"],
    "sources": { "weeklyHours": "DIALOGUE", "excludedKeywords": "DIALOGUE" }
  }
}
```

`mode` 为 `LLM` 或 `RULE`；`sources` 每个字段标记 `PROFILE` / `DIALOGUE` / `MANUAL`。

### 3.2 推荐结果

顶层字段：

```json
{
  "mode": "LLM",
  "fallbackReason": null,
  "generatedAt": "2026-10-20T20:21:00",
  "windowDays": 90,
  "confidence": "HIGH",
  "insight": "…",
  "profileSummary": { "…": "…" },
  "missingFields": [],
  "notes": ["分数只是相对匹配度…"],
  "competitions": [],
  "teams": [],
  "appliedTeams": []
}
```

竞赛卡片关键字段：`id,name,organizer,level,signupStart,signupEnd,startTime,tags,eligibilityText,category,categoryLabel,matchScore,evidenceCompleteness,reason,risks,nextStep,sourceType,weakDirection`。

队伍卡片关键字段：`id,competitionId,competitionName,title,captainName,memberCount,maxMembers,remainingPlaces,deadline,requiredSkills,memberSkillSummary,suggestedRole,category,categoryLabel,matchScore,evidenceCompleteness,reason,risks,nextStep`。

`appliedTeams` 只包含当前用户“待审批”的申请/邀请，单独归类，不重复推荐申请。

## 4. 规则与评分

### 4.1 竞赛类别

| 类别 | 条件 |
|---|---|
| `OPEN` 可报名 | 报名开始与截止都已知且覆盖当前时刻；或正文明确写“即日起报名/已开放报名”且截止未过 |
| `UPCOMING` 可提前准备 | 报名开始晚于当前时间且在窗口内 |
| `NEEDS_CONFIRM` 需确认 | 缺少报名截止、缺少报名开始且无“即日起报名”依据、开始晚于截止、日期矛盾 |
| 排除 | 报名截止 `<=` 当前时刻、超出时间窗口、已知资格不符、用户明确排除 |

截止时刻本身不可报名；默认窗口 90 天，可选 30/90/180/365 天。日期统一按 `Asia/Shanghai` 处理，服务端通过可注入 `Clock` 计算。

**只有截止时间不算“可报名”**：主办方可能尚未开放报名。仅当 `description` / `eligibility_text` 明确出现“即日起报名”“已开放报名”一类表述时，才按 `OPEN` 处理，并在理由里注明依据来自文字描述、报名入口仍需核对。

### 4.2 评分权重

| 类型 | 方向/兴趣 | 技能与经历 | 角色互补 | 时间适配 | 偏好/准备 |
|---|---:|---:|---:|---:|---:|
| 竞赛 | 35 | 30 | 0 | 20 | 15 |
| 队伍 | 15 | 30 | 30 | 15 | 10 |

- 没有证据的维度不填 0，也不计入分母；另返回 `evidenceCompleteness`。
- 档案几乎全空时 `confidence=EXPLORE`，不输出高置信结论。
- 分数是相对匹配度，不是获奖/录取概率；临近截止不会被强行排到前面。
- 竞赛按“可报名 → 可提前准备 → 需确认”排序，再按分数和截止时间。
- 队伍实际成员数通过 `team_member` 统计；推荐读取只按实时数量判断，不写回数据库（人数/满员状态由申请、审批、退出等持锁写路径修正），避免覆盖队长同时修改的字段。

### 4.3 队伍资格

排除：不存在、已结束、非招募中、已过截止、实际成员已满、本人是队长/成员、已有待审批申请或邀请、所属赛事报名已截止、用户明确排除该方向。

队伍简介明确写出“每周至少 X 小时”时才做硬性时间限制；明确写出“需获奖/参赛/项目经历”且档案无任何相关证据时才做经历限制。未声明的要求不会由模型补造。

### 4.4 时间与排除项

- 对话中的 `weeklyHours` 覆盖档案值参与打分，但不写回 `users`。
- 用户明确说“不参加硬件/建模”时，竞赛和队伍都会被硬过滤；模型不能把它们加回来。
- `weeklyHours=0` 按“未确定”处理，时间维度不计分并提示补全。
- `NOT_PARTICIPATE` / `ONLY_VIEW` 用户仍可主动浏览推荐，但结果会提示“不修改公开状态、不自动开启接受邀请”。
- 时间窗口以需求摘要里的 `windowDays` 为唯一权威来源：对话把窗口改成 30 天后，下一次推荐请求就提交 30 天，页面不再单独维护一份可能过期的值。
- 勾选“只看队伍”是硬筛选：后端不返回竞赛列表，页面直接切到队伍页签，并在说明里写明本次不返回竞赛。

## 5. 模型与降级

- 先用数据库与规则各召回最多 20 项，各取前 10 项进入模型；模型只能对这批 ID 重排和解释，最终每类最多 5 项。
- 模型 Schema 由服务端动态生成：`id` 是本次候选的 `enum`，数组长度固定，模型无法编造不存在的 ID 或漏掉候选。
- 校验内容包括：ID 白名单、重复 ID、候选覆盖、分数 0–100、字符串/数组长度、类型。任一项不合格整批回退规则结果，不混用半份结果。
- 模型超时、停机、未配置、输出非法时返回 `mode=RULE` 和可读的 `fallbackReason`；卡片理由仍由真实字段生成。
- 小模型把 `suggestedRole` 写成斜杠长串时，只保留第一个角色并限长 40 字（展示层清洗，不新增信息）。

## 6. 会话存储

- Redis key：`ai:advice:session:{sessionId}`，24 小时 TTL，最多保留最近 20 条消息（10 轮）。
- 会话包含 `userId`，读取时校验归属；他人会话返回 403。
- Redis 不可用时接口返回 **503**，不会退回“把别人的旧会话当成本次结果”。此时仍可使用 `profile-recommendations`。
- 单条消息最长 2000 字；每用户每分钟最多 20 次消息/推荐；摘要编辑必须带版本号，过期返回 **409**。
- **读取只续期**：`GET` 走 `EXPIRE`，不重写正文。旧实现把读到的整份 JSON 写回，和并发编辑交错时会覆盖新需求。
- **写入是原子版本比较**：编辑摘要和发送消息都用 Lua 脚本“比较 version 再 SET”。两个请求基于同一版本编辑时只有一个成功，另一个返回 409。
- 模型处理期间如果会话被清空，迟到的结果不会复活它：写入时发现 key 不存在，返回 **404**。

## 7. 配置

`backend/src/main/resources/application.yml`：

```yaml
advice:
  session-ttl-hours: ${ADVICE_SESSION_TTL_HOURS:24}
  max-messages: ${ADVICE_MAX_MESSAGES:20}
  max-message-length: ${ADVICE_MAX_MESSAGE_LENGTH:2000}
  max-requests-per-minute: ${ADVICE_MAX_REQUESTS_PER_MINUTE:20}
  default-window-days: 90
  min-window-days: 7
  max-window-days: 365
llm:
  provider: ${LLM_PROVIDER:ollama}
  model: ${LLM_MODEL:deepseek-r1:7b}
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:180}
```

前端推荐/对话请求单独使用 180 秒/120 秒超时，避免被全局 15 秒 axios 超时截断。

## 8. 常见错误码

| code | 场景 |
|---|---|
| 400 | 消息为空/超长、摘要字段非法、时间窗口越界 |
| 403 | 访问他人会话 |
| 404 | 会话不存在或过期 |
| 409 | 摘要版本号过期（其他页面已更新） |
| 429 | 触发频率限制 |
| 503 | Redis 会话存储不可用；仍可使用仅档案推荐 |

## 9. 演示步骤

1. 用 `student3 / 123456` 登录，打开“参赛推荐”。
2. 点“根据我的资料推荐”，观察竞赛/队伍两张列表、模式标签和相对分数提示。
3. 在“补充说明”输入“我只能投入 3 小时，不想参加建模了”，发送后查看“本次需求摘要”的来源标签。
4. 点“保存并重新推荐”，确认理由中的每周时间变为 3 小时，建模方向队伍消失。
5. 点“清空对话”，恢复按档案推荐。
6. 点竞赛“查看详情”进入 `/competitions/{id}`；点队伍“申请加入”进入 `/teams/{id}?apply=1`，仍需学生手动提交。

截图见 `docs/screenshots/advice-results-viewport.png`（真实 LLM 结果）与 `docs/screenshots/advice-recommendation.png`（完整页面）。
