# Apex NAS Docker 部署

本文用于把 Apex 部署到 `100.71.129.75`，复用 NAS 已有 MySQL。生产编排
文件不会创建、修改或删除 MySQL 容器和数据卷。

## 1. 端口和目录

- DSM 管理端口继续使用 `5000`，不要分配给 Apex。
- Apex 默认入口：`http://100.71.129.75:8088/`。
- 建议项目目录：`/volume1/docker/apex`。
- 只需放行前端端口 `8088`；后端 `8080` 不对 NAS 主机发布。

如果 `100.71.129.75` 是 Tailscale 地址，可以只允许 Tailscale 网络访问，不要
在路由器上把 `8088`、`8080` 或 `3306` 映射到公网。

## 2. 准备数据库账号

不要让应用长期使用 MySQL `root`。在 MySQL 中创建专用账号，并按实际密码替换
示例值：

```sql
CREATE USER IF NOT EXISTS 'apex_app'@'%' IDENTIFIED BY 'replace_with_a_strong_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON apex.* TO 'apex_app'@'%';
FLUSH PRIVILEGES;
```

默认配置按照 MySQL Docker 容器部署：数据库容器名为 `mysql`，Docker 网络名为
`mysql_default`。生产 Compose 会让后端同时加入 Apex 网络和这个外部数据库网络，
数据库无需向 Apex 发布宿主机端口。可通过下面的命令确认实际名称：

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Networks}}'
```

如果名称不同，在 `.env.production` 中修改 `MYSQL_HOST` 和
`MYSQL_DOCKER_NETWORK`。如果 MySQL 是 NAS 套件而非容器，则需要移除 Compose
中的外部 `mysql` 网络，并将 `MYSQL_HOST` 设置为容器可访问的 NAS 地址。

首次部署或升级前先备份 `apex` 数据库。数据库不在本项目的生产 Compose 生命周期
内，执行 `docker compose down` 不会删除数据库。

## 3. 放置代码和配置

私有仓库需要先给 NAS 配置独立的只读 GitHub Deploy Key，不要复制个人电脑上的
私钥。在 NAS 执行：

```bash
mkdir -p /root/.ssh
chmod 700 /root/.ssh
ssh-keygen -t ed25519 -C "Awe-NAS apex deploy" \
  -f /root/.ssh/id_ed25519_apex -N ""
cat /root/.ssh/id_ed25519_apex.pub
```

把输出的公钥添加到 GitHub 仓库 `Settings -> Deploy keys`，不要勾选写权限。然后
通过 SSH 登录 NAS 并克隆：

```bash
cd /volume1/docker
GIT_SSH_COMMAND="ssh -i /root/.ssh/id_ed25519_apex -o IdentitiesOnly=yes" \
  git clone git@github.com:aweew/apex.git apex
git -C apex config core.sshCommand \
  "ssh -i /root/.ssh/id_ed25519_apex -o IdentitiesOnly=yes"
cd apex
cp .env.production.example .env.production
chmod 600 .env.production
```

编辑 `.env.production`，至少替换以下字段：

```dotenv
MYSQL_HOST=mysql
MYSQL_DOCKER_NETWORK=mysql_default
MYSQL_USER=apex_app
MYSQL_PASSWORD=数据库强密码
APEX_LOCAL_PASSWORD=应用登录强密码
APEX_JWT_SECRET=至少32位随机字符串
```

`MYSQL_DOCKER_NETWORK` 可省略，默认使用 `mysql_default`。只有 MySQL 容器所在
网络名称不同时才需要配置该项。

可使用下面的命令生成 JWT 密钥：

```bash
openssl rand -hex 32
```

如需 AI 摘要，再设置 `APEX_AI_ENABLED=true` 和 `APEX_AI_API_KEY`。真实的
`.env.production` 已被 Git 忽略，也不会被复制进镜像。

## 4. 构建和启动

部署脚本只读取已有的 `.env.production`，不会从模板复制，也不会创建或覆盖生产
配置。完成首次配置后执行：

```bash
sh scripts/deploy-nas.sh
```

默认会先执行 `git pull --ff-only` 拉取最新代码，再构建、启动并验证服务。

按服务部署：

```bash
sh scripts/deploy-nas.sh --be  # 只部署后端
sh scripts/deploy-nas.sh --fe  # 只部署前端
```

单服务部署使用 Compose 的 `--no-deps`，不会连带重建或重启另一个服务。无参数时
仍会部署前后端全部服务。

### 安装全局命令

使用 root 用户在仓库中执行一次：

```bash
sh scripts/deploy-nas.sh --install-command
```

该命令会安装 `/usr/local/bin/deploy-nas.sh` 入口，入口始终调用当前仓库中的部署
脚本，不会复制或修改 `.env.production`。之后可以在任意目录执行：

```bash
deploy-nas.sh       # 部署前后端
deploy-nas.sh --be  # 只部署后端
deploy-nas.sh --fe  # 只部署前端
```

可使用 `command -v deploy-nas.sh` 确认全局命令已加入当前 PATH。仓库路径发生变化
后，在新仓库目录重新执行一次安装命令即可更新入口。

脚本也支持复制到仓库根目录后执行 `./deploy-nas.sh`，会自动识别同级的生产
Compose 文件。

脚本会依次检查 Docker、Compose、生产配置和 MySQL Docker 网络，构建并启动容器，
等待后端健康检查，然后从 NAS 宿主机验证 `127.0.0.1:8088`。失败时会自动输出
前后端最近 120 行日志，不会停止数据库或删除任何数据卷。

只检查配置，不启动容器：

```bash
sh scripts/deploy-nas.sh --check
```

也可以继续使用兼容参数执行同样的更新部署：

```bash
sh scripts/deploy-nas.sh --update
```

手工启动命令保留用于排障：

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

首次构建会下载 Maven、npm、Java、Python 和行情依赖，耗时取决于 NAS 性能和
网络。Vue 使用 Node 20 构建；后端使用 Java 17，并在运行镜像中安装 Python 3
及 `scripts/market_data/requirements.txt`。

群晖 Container Manager 也可以将 `docker-compose.prod.yml` 导入为“项目”。如果
界面不能选择 env 文件，可将 `.env.production` 另存为项目目录下的 `.env`，或在
项目环境变量页面逐项填写，仍不要提交该文件。

## 5. 验证

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail=100 backend
curl --fail http://100.71.129.75:8088/apex/api/health
```

健康接口返回成功后，浏览器打开：

- 应用：`http://100.71.129.75:8088/`
- Swagger：`http://100.71.129.75:8088/apex/swagger-ui.html`

前端和 API 使用同一地址，Nginx 将 `/apex` 转发给后端。浏览器不会访问自身的
`127.0.0.1:8080`。

## 6. 日常操作

查看日志：

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml logs -f
```

拉取代码并升级：

```bash
sh scripts/deploy-nas.sh --update
```

只重启应用：

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml restart
```

停止并保留持久数据：

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml down
```

不要添加 `-v`，否则会删除 Apex 的输出和日志卷。数据库仍由 NAS 上原来的部署
单独备份和恢复。

## 7. 常见问题

### 后端一直 unhealthy

先检查后端日志和网络。确认后端与 MySQL 共享 `mysql_default`，并且容器内能解析
数据库名：

```bash
docker inspect apex-backend-1 --format '{{json .NetworkSettings.Networks}}'
docker exec apex-backend-1 getent hosts mysql
```

其他常见原因是数据库账号只允许 `localhost` 登录、密码错误，或者数据库字符集/
表结构尚未升级。

### 页面能打开但接口返回 502

运行 `docker compose ... ps`，确认 `backend` 为 healthy。不要把前端的 API 地址
改成 NAS 的 `8080`；生产环境应始终通过 `/apex` 反向代理。

### 一键决策约 60 秒后返回 504

一键决策会同步扫描全市场，耗时可能超过一分钟。前端容器内的 Nginx 已将读取超时
设为 600 秒；还需要在 DSM「登录门户 → 高级 → 反向代理」中编辑 Apex 规则，进入
「自定义标题/高级」并将代理响应超时设为至少 600 秒。若 DSM 版本不提供该界面项，
需在实际位于最外层的反向代理中设置 `proxy_read_timeout 600s`。

后端日志出现“决策同步观察池”后又出现 `Broken pipe`，通常表示决策已经完成，但
外层代理提前关闭了浏览器连接；这时刷新智能决策页即可读取已落库的今日结果。

### NAS 内存较小

修改 `.env.production` 中的 `JAVA_TOOL_OPTIONS`，例如 2 GB 内存设备可先使用
`-Xms128m -Xmx512m`。执行 `up -d` 重新创建后端容器即可生效。
