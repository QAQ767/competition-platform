# 高校学科竞赛管理系统：小智配置与实现说明

适用日期：2026-10-21。发现竞赛 `/` 内提供参赛小智，训练中心 `/training` 保留学习小智。`/advice` 重定向到 `/?assistant=advice`。

## 服务端配置

把项目根目录 `.env.example` 中的 LLM 配置合并到本地 `.env`，保留原有数据库、JWT 和端口配置。不要用示例覆盖已有 `.env`。密钥仅填写在服务端本地 `.env` 或后端进程环境中。

```dotenv
LLM_ENABLED=true
LLM_PROVIDER=deepseek
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-flash
LLM_API_KEY=
LLM_TIMEOUT_SECONDS=45
LLM_ASSISTANT_TIMEOUT_SECONDS=120
LLM_ASSISTANT_MAX_TOKENS=1200
ADVICE_TURN_TIMEOUT_SECONDS=90
```

开发启动脚本和 Compose 兼容本地已有的 `apikey` 字段；推荐新配置统一使用 `LLM_API_KEY`，不要同时配置两种名称。直接启动 Java 不会自动读取项目 `.env`，须使用下面的脚本或自行向后端进程注入环境变量。

本机开发：先启动既有 MySQL、Redis，再在项目根目录执行：

```powershell
.\scripts\start-backend.ps1 -Port 8080
# 另一终端
Set-Location frontend
npm run dev
```

开发入口默认为 `http://localhost:5173`，Vite 默认代理 `8080`。需要验证其他后端时，在启动 Vite 的同一终端设置 `$env:BACKEND_TARGET='http://127.0.0.1:8083'`。这是代理配置，不是前端密钥配置。上传和 WebSocket 原默认代理保持不变，独立验收实例只验本任务功能。

Docker 使用根目录 `.env` 注入**后端**，不向前端传入密钥。完成备份并确认部署范围后可执行 `docker compose up -d --build backend frontend`；本次交付不自动执行部署。保留 `BACKEND_PORT=8081` 时后端宿主端口仍为 8081，容器内部仍为 8080。不要执行 `down -v` 或覆盖数据库。`start-all.bat` 不再强制启动 Ollama；只启动容器，不保证镜像已包含最新源码。

切回 Ollama：设置 `LLM_PROVIDER=ollama`、`LLM_MODEL=deepseek-r1:7b`，本机 `LLM_BASE_URL=http://localhost:11434`，Docker 使用 `http://host.docker.internal:11434`；自行启动 Ollama。关闭模型用 `LLM_ENABLED=false` 或 `LLM_PROVIDER=none`：参赛推荐明确显示规则结果，学习助手明确告知模型未启用。云端失败不会静默切换到 Ollama。

确认实际 provider：查看后端 `LLM request=... provider=... model=...` 日志，并发送一次真实问题。配置存在、启动成功或 HTTP 200 均不等于模型成功；推荐应查看 `mode`、`failureCategory`，学习流应收到非空 `chunk` 和 `done`。日志不记录 Authorization、密钥或完整私人对话。

截至本文日期，官方文档推荐 `deepseek-flash`；Chat Completions 使用 `json_object`，没有将它冒充为服务端严格 JSON Schema。结构化任务采样温度为 0.2，学习文本为 0.5；两者关闭内部思考输出。结构定义同时写入提示词，业务校验始终由本服务执行。参考：[首次调用](https://api-docs.deepseek.com/zh-cn/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)。

## 接口与状态

继续复用 `AdviceSessionService`、`AdvicePreferenceService`、`StudentRecommendationService`、资格规则与 Redis Lua 比较写入。原分步接口和档案推荐接口保留兼容。

新增 `POST /api/ai/advice/sessions/{id}/chat`：请求 `{"message":"我会 Java，每周5小时，推荐竞赛和队伍","version":0}`，返回登录用户会话的 `sessionId/version/reply/questions/preferences/mode/fallbackReason/recommendation`。`recommendation` 包含后端真实卡片、`retryCount/failureCategory`。需要确认赛事或处理矛盾时，`questions` 最多两项、回复直接展示追问、`recommendation=null`；这时 `mode=LLM` 只代表理解阶段用了模型，不能当作推荐业务成功。普通问候、能力说明和推荐解释不强制生成卡片。客户端无须保存摘要或二次点击推荐。

新增 `POST /api/ai/advice/sessions/{id}/cancel`：校验会话所有权，提升会话版本并取消当前模型等待。版本提升同时阻止取消先到、旧请求后到时提交旧结果。客户端发送前重读版本；同一会话生成中拒绝重复发送。并发编辑返回 409，删除/过期返回 404，跨用户返回 403。

聊天回复和完整校验后的卡片一次保存。Redis 保留原 24 小时 TTL 和原子版本比较，读取只续期。关闭再开、刷新可恢复当前用户的服务端会话。Redis 故障明确报错，不声称记住未保存的需求；原不依赖 Redis 的 `/profile-recommendations` 仍可单独调用。

临时偏好不写公开档案。明确赛事名称保存在 `competitionKeywords`，由平台真实赛事目录解析为 `resolvedCompetitionIds`。`competitionIntentStatus` 为 NONE、RESOLVED、UNRESOLVED 或 AMBIGUOUS；完整名称不要求以“杯”结尾。未找到或简称有歧义时，保留原始名称并追问，不放宽到无关赛事。选定身份后，对比赛及关联队伍同时实施硬过滤；取消赛事限制须明确表达。

赛事身份必须有命名证据：目录完整名称、杯赛族名、可辨识简称、明确引述名称或赛事专名结构；不能将“推荐”后任意文本当成赛事名。数量、难度、方向、技能和时间只作为一般筛选条件，“适合新手的比赛”“算法类比赛”“算法相关赛事”均不建立赛事身份。模型提出的名称也必须经过同样验证，目录标题中仅出现“算法”等方向词不构成简称证据。追加普通条件保留已有明确赛事；若已有赛事尚未确认，追加普通条件也不会自动清除它。

输出目标 `outputTarget` 为 COMPETITIONS、TEAMS 或 BOTH，旧 `teamOnly` 保留兼容；“不找队伍了，只推荐比赛”只返回比赛，“也看看比赛”扩展到两类。目标改变不丢弃关联赛事身份。换一批记录已展示 ID，不放宽用户明确条件。

需求变更按字段合并：模型只输出本轮明确变化、通过类型和范围验证后保留；规则只对本轮确实识别的字段纠偏，不再用旧值覆盖模型理解。支持中文数字、撤销排除和 CLEAR 操作。`weeklyHoursUnrestricted/windowUnrestricted` 明确表示取消本轮每周时间或未来日期范围限制，避免空值再次回落成旧偏好；报名截止、平台资格、队伍人数等规则仍保留。明确赛事与排除项矛盾时先追问。

面板打开时会话读取或发送返回404，会清除内存和浏览器的旧会话引用，保留本次问题；点击重试创建新 ID，不复活旧会话。网络错误或503保留原 ID、历史和需求，再次重试仍访问原会话；关闭重开时同样只把明确404视为失效。

卡片名称、ID、日期、人数和跳转路径来自平台记录；模型仅提供排序、理由等受限字段。白名单、唯一性、候选完整性、类型、范围和长度验证全部通过后才写模型字段。失败整批回退，不保留部分模型理由。返回前重查资格、申请和人数；失效卡片移除后不沿用旧概括。既有管理员抓取、草稿审核发布、队长推荐流程未重写。

## 模型、流式与时间预算

`LlmService` 统一承接 DeepSeek 与 Ollama 的非流式/流式传输；学习和参赛分别维护提示词与上下文。DeepSeek 按 SSE 事件解析，Ollama 按 NDJSON 解析；忽略 `reasoning_content`，兼容跨片段 think 标签过滤，缺结束标记、截断及上游错误均明确终止。

参赛整个回合预算最高 90 秒，可用 `ADVICE_TURN_TIMEOUT_SECONDS` 调低；单次调用默认 45 秒，只使用整轮剩余预算。仅业务校验失败允许最多一次纠错，且剩余预算充足才发起。401/403/402、429、断网或超时后，本轮后续阶段直接沿用该故障并给规则结果，不重复请求同一个不可用服务。

客户端参赛请求及回合定时器为 100 秒；学习客户端 125 秒、后端默认 120 秒；Vite API 代理 135 秒；Nginx 学习 SSE 130 秒并关闭缓冲，REST 210 秒。学习超时配置应保持不超过 120 秒，若调整需同步核查全链路。普通 Axios 仍为 15 秒，聊天请求单独覆盖。

客户端停止/关闭/新对话会取消接收，用代际编号和用户 ID 隔离迟到结果；参赛还请求服务端取消。学习 SSE 心跳用于发现断连，适配层定时检查取消并关闭流，释放阻塞读取。页面卸载、退出及切换用户清理相应状态。

云端模式发送完成本任务所需的专业、技能、时间、有限简介/项目摘要和候选字段；不发送账号 token、密码、联系方式等无关字段。前端显示简短云端处理说明。不要把密钥写入 `VITE_*`、浏览器存储、源码、截图或报告。

## 验证命令

```powershell
Set-Location backend
mvn -o test -q
mvn -o -DskipTests package -q
Set-Location ../frontend
node --test tests/*.test.js
npm run build
```

自动测试中的 DeepSeek 使用本地 HTTP 模拟服务，明确区别于真实云端验收。浏览器截图、真实指标和本轮交接记录保留在个人验收材料中，不纳入代码提交。
