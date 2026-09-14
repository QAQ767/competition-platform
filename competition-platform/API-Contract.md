# Demo 版 API 接口契约（前后端共同遵守）

> 本文件是 demo 版前后端开发的唯一接口约定。后端实现接口，前端按此调用。
> Base URL：`http://localhost:8080/api`　数据格式：JSON

## 0. 通用约定

2026-09 扩展：备赛任务的阶段、优先级、资料关联、完成复盘、任务编辑和计划生成接口，以及邀请通知关联字段，见 [备赛计划接口与兼容约定](docs/preparation-plan.md)。

- 所有响应统一包装：`{ "code": 200, "message": "success", "data": ... }`；`code != 200` 表示失败，前端弹 message 提示。
- 认证方式：登录后返回 `accessToken`、`refreshToken` 和 `user`；请求头携带 `Authorization: Bearer <accessToken>`。访问凭证过期时使用 `/auth/refresh` 刷新，并发失败请求共用一次刷新。
- 公开接口（无需登录）：`GET /competitions`、`GET /teams`、`GET /teams/{id}`、`POST /auth/login`、`POST /auth/register`。其余接口需登录，未登录返回 `{code:401}`。
- 日期格式：`yyyy-MM-dd HH:mm:ss`。

## 1. 实体字段（前后端字段名必须一致）

```
User:  { id, username, nickname, avatar, email, college, major, intro,
         role("STUDENT"/"ADMIN"), competeStatus, skills:[{id,name}], createdAt }
Competition: { id, name, organizer, level("校级"/"省级"/"国家级"), signupStart,
               signupEnd, startTime, description, status("报名中"/"即将开始"/"已结束") }
Team:  { id, competitionId, competitionName, captainId, captainName, title,
         description, memberCount, maxMembers, status("招募中"/"已满员"/"备赛中"/"已结束"),
         deadline, skills:[String 技能名], createdAt }
TeamApplication: { id, teamId, userId, userName, intro, status("待审批"/"已通过"/"已拒绝"),
                   invited(boolean), applyTime }
TeamMember: { id, teamId, userId, userName, role("队长"/"队员"), joinedTime }
RecommendUser: { userId, nickname, college, major, skills:[String],
                 competeStatus, matchScore(0~100), reason, hasExperience(boolean) }
Notification: { id, type, content, isRead, createTime, inviteId?, inviteStatus? }
Stats: { userCount, teamCount, competitionCount, achievementCount,
         hotCompetitions:[{name, teamCount}] }
```

**competeStatus 枚举（参赛状态）**：
`WANT_INVITE`=希望被邀请　`ACCEPT_INVITE`=可接受邀请　`NOT_PARTICIPATE`=暂不参赛　`ONLY_VIEW`=仅浏览

## 2. 接口列表

### 认证 Auth
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| POST | /auth/register | {username,password,email,college,major} | {accessToken, refreshToken, user}（注册后自动登录） |
| POST | /auth/login | {username,password} | {accessToken, refreshToken, user} |
| POST | /auth/refresh | {refreshToken} | {accessToken, refreshToken, user} |
| GET | /auth/me | Authorization: Bearer | user |

### 用户 User
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| PUT | /user/profile | {nickname,intro,avatar,college,major,email}（字段可选） | user（更新后的完整资料） |
| PUT | /user/compete-status | {competeStatus} | user |
| GET | /user/me/teams | — | [Team]（我创建的 + 我加入的） |
| GET | /user/me/applications | — | [TeamApplication]（我发出的申请） |

资料更新仅作用于当前登录用户；未提交或为 `null` 的字段保持原值。邮箱会去除首尾空格，非空时校验邮箱格式及最长 100 字符，传 `email: ""` 可清空邮箱。只上传头像不会清空其他资料。

头像在个人中心先本地裁剪再上传：原图最大 50MB，支持 JPG、PNG、WebP、GIF、BMP（动态图取静态画面）。圆形预览支持拖动、方向键移动和滑块／滚轮缩放；输出 512×512 JPG（白底、质量 0.88、最大 1MB），头像控件以圆形显示。仅在点击保存时上传压缩后的图片到 `/files/upload`，再更新 `/user/profile` 的 `avatar`。取消裁剪不上传；资料更新失败时可重试并复用已上传文件。服务器通用附件上限仍为 20MB，原图不传到服务器。

### 关注与粉丝
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /users/me/following | — | [User]（我关注的人，按关注时间倒序） |
| GET | /users/me/followers | — | [User]（关注我的人，按关注时间倒序） |
| POST | /users/{id}/follow | — | —（关注或回关，重复请求不新增记录） |
| DELETE | /users/{id}/follow | — | —（取消我对该用户的关注） |

以上接口需登录，列表归属由登录上下文确定。个人中心展示两类列表及人数，支持昵称、用户名和院系搜索、每页 6 人的前端分页；互相关注状态取两份列表的交集。操作成功后同步更新本地列表和人数，失败时保留原状态；可刷新重新获取列表。取消关注不会删除对方对我的关注。

### 竞赛 Competition
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /competitions | 可选 query: keyword,level,status | [Competition] |
| POST | /competitions | {name,organizer,level,signupStart,signupEnd,startTime,description} | Competition |
| DELETE | /competitions/{id} | — | — |

### 组队 Team

组队截止规则：后端启动后每 30 秒检查一次，将 `deadline <= 当前时间` 且尚未结束的队伍（招募中、已满员、备赛中）更新为 `已结束`。未设置截止时间的队伍不受影响；已有队员、资料、训练任务保留。申请、邀请、批准申请和接受邀请均即时校验截止时间，不依赖下一轮扫描。到期满员队伍因成员退出或移除出现空位时不会恢复招募。组队广场和详情页每 30 秒刷新状态，已结束队伍关闭申请入口并禁用批准／邀请操作。

| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /teams | 可选 query: competitionId,skill,status,keyword | [Team] |
| GET | /teams/{id} | — | {team, members:[TeamMember], applications:[TeamApplication]} |
| POST | /teams | {competitionId,title,description,maxMembers,skills:[String],deadline} | Team（创建者即队长） |
| POST | /teams/{id}/apply | {intro} | TeamApplication |
| POST | /teams/{id}/applications/{appId}/approve | — | —（队长操作，写入消息通知） |
| POST | /teams/{id}/applications/{appId}/reject | — | — |
| POST | /teams/{id}/invite | {userId} | —（写一条 INVITE 通知给被邀请人） |

### AI 推荐（核心亮点）
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /ai/recommend | ?teamId= | [RecommendUser] |

**AI 推荐规则（demo 用规则引擎实现，代码预留大模型 API 扩展位）**：
1. 候选池 = 全部用户中 competeStatus ∈ {WANT_INVITE, ACCEPT_INVITE}，且不是该队队长/成员、未申请过该队。
2. 匹配度 = 技能契合度(40) + 竞赛方向契合度(30) + 参赛状态契合度(20) + 经验契合度(10)。
   - 技能契合度：用户技能与队伍所需 skills 的交集数 / 队伍所需技能数 × 40。
   - 竞赛方向契合度：用户简介/学院与竞赛关键词的相关性，规则近似（有交集给 15~30，否则 10）。
   - 参赛状态契合度：WANT_INVITE 给 20，ACCEPT_INVITE 给 12。
   - 经验契合度：有成果/获奖记录给 10，否则 5。
3. 推荐理由模板（按得分最高的维度生成）：
   - "TA 擅长【技能1、技能2】，与你的队伍需求高度契合"
   - "TA 当前正在备战，希望被邀请入队"
   - "TA 有过竞赛获奖经历，经验丰富"
   - 组合 2~3 条，用"；"连接。
4. 每条结果含 matchScore(取整) 与 reason。
5. 代码中定义 `AiService` 接口 + `RuleAiServiceImpl` 实现；预留注释说明可替换为调用 DeepSeek/通义千问 API 生成 reason（demo 不真调 API）。

### 通知 Notification
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /notifications | — | [Notification]（按创建时间倒序；邀请状态独立于已读状态） |
| POST | /notifications/{id}/read | — | — |

### 数据统计 Stats
| 方法 | 路径 | 请求 | 返回 data |
|---|---|---|---|
| GET | /stats/overview | — | Stats |

## 3. 演示种子数据要求（后端 data.sql 预置）

- 用户 8 个：密码统一 `123456`（BCrypt 加密），角色 student1~student7 为 STUDENT、admin1 为 ADMIN。
  覆盖四种 competeStatus：3 个 WANT_INVITE、2 个 ACCEPT_INVITE、2 个 NOT_PARTICIPATE、1 个 ONLY_VIEW。
- 技能标签：算法、前端、后端、UI设计、文档写作、数据分析、机器学习、答辩演讲。
- 竞赛 5 个：数学建模（国家级）、蓝桥杯（省级）、ACM校赛（校级）、互联网+创新创业（国家级）、挑战杯（省级）。
- 队伍 5 个（状态覆盖招募中/已满员/备赛中），部分队伍带成员与待审批申请。
- 通知若干条便于演示红点。
