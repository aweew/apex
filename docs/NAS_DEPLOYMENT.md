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

如果 MySQL 是 NAS 上的 Docker 容器，需要确认它已发布 `3306`，或者把 Apex
后端加入该容器所在网络并将 `MYSQL_HOST` 改成 MySQL 容器名。如果 MySQL 是 NAS
套件，则确认它监听 NAS/Docker 可访问的地址，并允许 Docker 网段登录。

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
MYSQL_HOST=100.71.129.75
MYSQL_USER=apex_app
MYSQL_PASSWORD=数据库强密码
APEX_LOCAL_PASSWORD=应用登录强密码
APEX_JWT_SECRET=至少32位随机字符串
```

可使用下面的命令生成 JWT 密钥：

```bash
openssl rand -hex 32
```

如需 AI 摘要，再设置 `APEX_AI_ENABLED=true` 和 `APEX_AI_API_KEY`。真实的
`.env.production` 已被 Git 忽略，也不会被复制进镜像。

## 4. 构建和启动

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  up -d --build
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
git pull --ff-only
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
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

先检查后端日志。常见原因是 MySQL 没有监听 Docker 可访问的地址、账号只允许
`localhost` 登录、NAS 防火墙拦截 Docker 网段，或者数据库字符集/表结构尚未升级。

### 页面能打开但接口返回 502

运行 `docker compose ... ps`，确认 `backend` 为 healthy。不要把前端的 API 地址
改成 NAS 的 `8080`；生产环境应始终通过 `/apex` 反向代理。

### NAS 内存较小

修改 `.env.production` 中的 `JAVA_TOOL_OPTIONS`，例如 2 GB 内存设备可先使用
`-Xms128m -Xmx512m`。执行 `up -d` 重新创建后端容器即可生效。
