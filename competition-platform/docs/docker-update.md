# 更新现有 Docker 项目

在 `competition-platform` 目录执行：

```powershell
docker compose build backend frontend
docker compose up -d --no-build
docker compose ps
docker compose logs --tail=80 backend
```

访问 `http://localhost`。更新会重建前后端容器，继续使用现有 `mysql-data`、`redis-data` 和 `uploads-data` 数据卷。不要使用 `docker compose down -v`，它会删除数据卷。若 8080 被本地 Java 后端占用，先停止该项目的本地后端，再启动 Docker 后端。

也可以在未跟踪的 `.env` 中设置 `BACKEND_PORT=8081`，保留本机 8080 服务。Docker 前端仍访问 `http://localhost`，容器内部代理仍使用 `backend:8080`；宿主机直连 Docker API 使用 `http://localhost:8081`。本机 MySQL 与 Docker MySQL 数据卷是不同数据库，不会自动同步。

学生参赛推荐及管理员赛事发现部署时，Compose 已传入 `LLM_TIMEOUT_SECONDS`（默认180）、`SEARCH_PROVIDER`（默认none）和 `BRAVE_SEARCH_API_KEY`（默认空）。真实搜索需另行配置密钥，未配置时使用公告URL/文本导入。Docker 部署固定 `DISCOVERY_ALLOW_FAKE_IP=false`，不放宽抓取地址校验。

仅拉取代码或重启旧容器不会更新镜像；必须执行构建和容器替换。前端镜像使用 `npm ci` 按锁文件安装依赖，包含邮箱修改、关注粉丝列表和头像裁剪功能。Nginx 已配置 `/uploads/` 代理。

若容器内 Maven 下载依赖缓慢，也可先在本机执行 Maven 测试与打包，再使用同一份 JAR 构建运行镜像（本次采用此方式）：

```powershell
mvn -f backend/pom.xml test package
docker build -f backend/Dockerfile.runtime -t competition-platform-backend backend/target
docker compose build frontend
docker compose up -d --no-build
```

后端组队截止任务每 30 秒执行一次，到期队伍更新为“已结束”；容器时区为 `Asia/Shanghai`。到期队伍存在时，日志会出现“组队截止检查”。截止后即使尚未执行下一轮扫描，申请、邀请和入队审批也会被拒绝。

本次替换前的数据库和上传文件备份保存在工作区忽略目录 `.tools/docker-backup-before-deadline-20260914`，不会提交到 GitHub。旧前后端镜像保留为 `competition-platform-backend:before-deadline-20260914` 和 `competition-platform-frontend:before-deadline-20260914`，用于本机回退。
