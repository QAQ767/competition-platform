# 高校学科竞赛管理系统（Demo 版）

> 毕业设计选题：**高校学科竞赛管理系统**
> 本目录为可运行的 Demo 版（前后端分离 + MySQL 8 + 演示数据）。

2026-10-21 更新：发现竞赛内的小智通过一句话直接返回竞赛与队伍卡片，旧 `/advice` 自动跳转；训练小智保留学习问答与流式输出。模型默认接入 DeepSeek 云端，支持切回 Ollama，密钥仅由后端读取。配置、接口、取消和时间预算见 [小智与 DeepSeek 配置说明](docs/xiaozhi-deepseek.md)。

2026-10 更新：个人资料新增项目经历、期望角色和每周可投入时间；队友推荐在规则筛选后，结合队伍目标与成员资料调用模型分析团队缺口、重新评分并说明建议角色。模型不可用时保留规则结果。更新后请同时构建前端并重启后端，后端会自动补齐旧库字段。详见 [AI 组队分析说明](docs/ai-team-assessment.md)。

2026-10-14 演示数据补充：7 个原始学生账号的空白协作档案、16 个新增虚构学生、10 场演示赛事、14 支互补能力队伍。新库自动导入；旧库备份后通过 `--demo.scenarios.enabled=true` 显式导入一次。赛事日期相对首次导入日生成，重启不会重复插入或顺延。账号及场景详见 [演示数据说明](docs/demo-scenarios.md)。

---

## ✨ 功能一览

| 模块 | 说明 |
|---|---|
| 竞赛信息 | 竞赛发布、检索、级别/状态筛选、报名倒计时；区分“即将报名”“报名即将截止”和开赛前 7 天的“即将开赛” |
| 组队广场 | 组队需求发布、按竞赛/技能/状态筛选 |
| 🤖 AI 智能推荐队友 | 先按参赛意愿与技能等规则筛选，再由模型结合个人经历、期望角色、投入时间和已有成员分析缺口、重新排序；失败时展示规则结果，支持一键邀请 |
| 个人协作档案 | 维护个人简介、项目／竞赛经历、期望角色与每周可投入时间，供队长查看和 AI 组队分析 |
| 🪄 小智 AI 辅助训练 | 训练中心 DeepSeek / Ollama 流式问答，支持知识讲解、代码分析、训练计划、练习出题及资源上下文 |
| 参赛状态设置 | 希望被邀请 / 可接受邀请 / 暂不参赛 / 仅浏览（决定是否进入 AI 推荐候选池，保护隐私） |
| 团队管理 | 入队申请审批、成员管理、邀请入队、**踢出队员（必填理由）**、**解散队伍** |
| 📂 训练资料 | 队伍共享文件上传/下载（成员权限，≤20MB） |
| 📝 任务看板 | 队长建任务分配负责人，成员更新状态 |
| 🏆 成果墙 | 获奖展示；**发布需带证明文件→管理员审核通过后才公开**，删除仅管理员 |
| ⭐ 关注功能 | 关注用户→看主页（资料+获奖记录）+私聊；**对方回复或关注回来前只能发 1 条私聊** |
| 🖼️ 头像 | 用户自定义头像（上传/展示于导航栏/个人中心/主页） |
| 🚫 脏话审查 | 大厅/私聊/讨论区：规则词库即时拦截 + **AI（本地 LLM）审查边界词** |
| 消息通知 | 站内消息 + 未读红点 |
| 💬 大厅聊天 | 公共聊天室，**左键私聊、右键看资料**（用户名/主页/关注/私聊菜单，**WebSocket 实时推送**） |
| 💬 私聊 | 一对一私聊：会话列表、未读数、消息已读（**WebSocket 实时**） |
| 💬 团队讨论区 | 队伍内部讨论（**WebSocket 实时推送**，仅成员可见） |
| 📮 赛事反馈 | 平台没有想参加的赛事→提交反馈给管理员，管理员标记处理 |
| 数据统计 | 平台概览 + 竞赛热度排行（ECharts） |
| 后台管理 | 竞赛增删改查 + **反馈处理**（演示） |

## 🗂️ 目录结构

```
competition-platform/
├── API-Contract.md          # 前后端接口契约（开发约定）
├── maven-settings.xml       # 自定义 Maven 配置（本地仓库+阿里云镜像）
├── backend/                 # SpringBoot 2.7.18 后端
│   ├── pom.xml
│   ├── src/main/java/com/campus/competition/...
│   └── src/main/resources/  # application.yml + schema.sql
└── frontend/                # Vue3 + Vite + Element Plus 前端
    ├── package.json
    └── src/...
```

## 🖥️ 环境要求

- **方式 A（Docker 一键部署，推荐）**：安装 Docker Desktop（已配置国内镜像加速）
- **方式 B（本地手动）**：JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.0、Redis

## 🚀 运行步骤

### 方式 A：Docker 一键部署（4 个容器：MySQL+Redis+后端+前端）

```bash
# 在 competition-platform 目录下执行
docker compose up -d --build
```

- 浏览器访问 **http://localhost**（Nginx 托管前端 + 反代 /api 和 /ws）
- 首次启动自动建库（容器内 MySQL）并播种演示数据，数据存 Docker 卷（重启不丢）
- 敏感配置在 `.env`（DB_PASSWORD / JWT_SECRET），已实现环境变量注入
- 小智默认使用 DeepSeek，先按 [配置说明](docs/xiaozhi-deepseek.md) 在服务端 `.env` 填写密钥；仅选择 Ollama 时需要拉取并运行本地模型。
- 常用命令：`docker compose down`（停止）、`docker compose ps`（状态）、`docker compose logs -f backend`（日志）

### 方式 B：本地手动部署

### 1. 启动后端（端口 8080）

```bash
# 开发模式直接运行（使用自定义 settings 以正确解析依赖）
mvn -s ..\maven-settings.xml -f backend\pom.xml spring-boot:run

# 或打包后运行
mvn -s ..\maven-settings.xml -f backend\pom.xml -q clean package -DskipTests
java -jar backend\target\competition-platform-0.0.1-SNAPSHOT.jar
```

> 数据库连接：`jdbc:mysql://localhost:3306/competition_platform`（账号 root/123456）。
> 改密码请编辑 `application.yml` 的 `spring.datasource` 后重新打包。
> 无 MySQL 环境时，pom.xml 里已注释保留 H2 依赖，切换数据源即可（备选方案）。

### 2. 启动前端（端口 5173）

```bash
cd frontend
npm install --cache ..\.npm-cache --registry https://registry.npmmirror.com
npm run dev
```

浏览器访问 **http://localhost:5173**（Vite 已配置 /api 代理到 8080）。

### 3. 演示账号

| 账号 | 密码 | 角色 | 参赛状态 |
|---|---|---|---|
| student1 | 123456 | 学生 | 希望被邀请（在 AI 推荐候选池） |
| student2 | 123456 | 学生 | 希望被邀请（在 AI 推荐候选池） |
| student3 | 123456 | 学生 | 可接受邀请 |
| student4 | 123456 | 学生 | 可接受邀请 |
| student5 | 123456 | 学生 | 暂不参赛 |
| student6 | 123456 | 学生 | 暂不参赛 |
| student7 | 123456 | 学生 | 仅浏览 |
| admin1 | 123456 | 管理员 | 仅浏览 |

**演示路径建议**：student1 登录 → 组队广场创建队伍 → 队长视角"AI 推荐队友"查看匹配结果并邀请 → student2 登录查看通知 → 个人中心切换参赛状态。

## 🧪 冒烟测试接口

```bash
curl http://localhost:8080/api/competitions
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student1\",\"password\":\"123456\"}"
# 登录后携带 Bearer Token 访问受保护接口
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
```

## ✅ Demo 已验证项（2026-08 实测）

| 场景 | 结果 |
|---|---|
| 登录（student1~7 / admin1，密码 123456） | ✅ 返回 token + 用户信息（含技能、参赛状态） |
| 竞赛列表 / 按级别状态筛选 | ✅ 状态每 30 秒按报名开始、临近截止和已截止时间自动计算 |
| 组队广场 / 创建组队 | ✅ 创建后自动成为队长 |
| 入队申请 → 队长审批 → 成员数/状态联动 | ✅ 满员自动标记"已满员"，双向站内通知 |
| 🤖 AI 推荐队友 | ✅ 候选池仅含"希望被邀请/可接受邀请"用户；匹配度=技能40+方向30+状态20+经验10；**前 3 名理由由本地 deepseek-r1 大模型生成（LLM），其余规则模板，异常自动降级**；一键邀请成功 |
| 🪄 小智 AI 辅助训练 | ✅ 登录后多轮问答、SSE 流式输出、训练场景上下文、停止生成、本地历史、限流及明确的模型故障提示 |
| 参赛状态切换 | ✅ 切换后立即影响推荐候选池（隐私开关） |
| 消息通知红点 / 已读 | ✅ 未读在前，点读生效 |
| 踢出队员（带理由） | ✅ 队长操作，理由必填，被踢者收到带理由通知，成员数/状态联动 |
| 解散队伍 | ✅ 全体成员收到通知，队伍及申请数据清理 |
| 💬 大厅聊天 | ✅ **WebSocket 实时推送**（首个消息 AUTH 认证），断线自动重连+轮询兜底 |
| 💬 私聊 | ✅ WebSocket 实时 + 会话列表 + 未读数 + 已读标记 |
| 💬 团队讨论区 | ✅ WebSocket 实时推送（仅成员可见，非成员收不到）+ 分页加载更早 |
| 📂 训练资料 | ✅ 文件上传（类型白名单/20MB 限制）+ 成员权限控制 + 静态访问 |
| 📝 任务看板 | ✅ 队长建任务分配负责人，负责人/队长更新状态，非负责人被拒 |
| 🏆 成果墙 | ✅ **发布需证明→待审核→管理员通过才公开**；学生删除被拒（403），管理员可删 |
| ⭐ 关注功能 | ✅ 关注/取关、用户主页（资料+获奖）、**私聊门控**：未关注被拒、单条限制、回复即解锁 |
| 🖼️ 头像 | ✅ 上传/更新 + 导航栏/主页展示 |
| 🚫 脏话审查 | ✅ 规则词库即时拦截；**AI（deepseek-r1）边界词审查**（"猪头"等被拦截） |
| 💬 右键菜单 | ✅ 大厅右键昵称 → 用户名/昵称 + 查看主页/私聊/关注 |
| 📄 分页加载 | ✅ 队伍/大厅/私聊/成果/反馈/讨论区全部分页（MyBatis-Plus 分页插件） |
| 📮 赛事反馈 | ✅ 学生提交（如"平台没有的赛事"）→ 管理员列表处理 |
| 数据统计 | ✅ 4 项计数 + ECharts 竞赛热度柱状图 |
| 🔐 JWT 登录认证 | ✅ 登录返回 access+refresh 双 Token；Bearer 头鉴权 |
| 🔐 Token 刷新 | ✅ 401 自动用 refreshToken 刷新并重放请求，失败才跳登录 |
| 🔐 权限控制 | ✅ 学生访问管理接口返回 403，管理员正常（@PreAuthorize） |
| 🚦 登录防爆破 | ✅ Redis 计数，5 次失败锁定 5 分钟（429） |
| 🚪 主动下线 | ✅ 登出后 access token 进 Redis 黑名单，立即 401 |
| 🔄 Token 轮换 | ✅ refresh 用过即废（黑名单防重放） |
| 🧊 缓存 | ✅ 竞赛列表(60s+抖动)/统计(300s+抖动)，写操作自动失效 |
| 🐳 Docker 部署 | ✅ 4 容器一键启动（MySQL/Redis/后端/前端-Nginx），REST+WebSocket 代理全通 |
| 📜 操作日志 | ✅ AOP 统一审计（登录/注册/建队/审批/踢人/上传等 18+ 动作），管理员分页查看 |
| 前后端联调 | ✅ Vite 代理 /api → 8080，全链路通过 |

## ⚠️ 环境备注（本机沙箱环境特有，非代码问题）

- 本沙箱环境限制 npm 子进程（EPERM），安装时需加 `--ignore-scripts`；普通电脑上直接 `npm install` 即可。
- Maven 需使用 `-s ..\maven-settings.xml`（本地仓库在 `D:\Moon\.m2repo`，镜像为阿里云）。

## 🛠️ 技术栈

后端：SpringBoot 2.7.18 / MyBatis-Plus 3.5.5 / MySQL 8 / **Spring Security + JWT** / **Redis** / **WebSocket** / **文件上传（本地存储）** / BCrypt
前端：Vue 3 / Vite 5 / Element Plus / Pinia / Vue Router / Axios / ECharts

## 📌 Demo 与正式版差异（答辩口径）

| 项 | Demo（当前） | 正式版 |
|---|---|---|
| 数据库 | **MySQL 8.0**（本机） | MySQL 8.0（云上） |
| 缓存 | **✅ Redis（已完成：缓存/黑名单/限流）** | 分布式锁防超员、验证码缓存 |
| 认证 | **✅ Spring Security + JWT（已完成）** | 补 Redis 黑名单/强制下线、登录限流 |
| AI 推荐 | **✅ 规则引擎 + DeepSeek 云端 / Ollama 可配置模型** | 继续按服务端配置选择 provider |
| 部署 | 本地双进程 | Nginx + 云服务器 |

---

*配套文档：`../竞赛组队平台-技术方案与功能模块.md`、`../开题报告.md`*
