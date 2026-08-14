# OpenClaw 群晖部署

该部署与 Apex 容器独立，固定使用阿里云镜像仓库中的 OpenClaw
`2026.7.1-2`。Gateway 只发布到 NAS 回环地址，通过 SSH 隧道访问，不挂载
Docker socket。

## 1. 在 NAS 准备目录

先把 Apex 最新代码放到 `/volume1/docker/apex`，然后登录 NAS：

```bash
ssh apex-nas
sudo -i
mkdir -p /volume1/docker/openclaw/data/state
mkdir -p /volume1/docker/openclaw/data/workspace/skills
cp /volume1/docker/apex/integrations/openclaw/deployment/compose.yaml \
  /volume1/docker/openclaw/compose.yaml
cp /volume1/docker/apex/integrations/openclaw/deployment/.env.example \
  /volume1/docker/openclaw/.env
cp -R /volume1/docker/apex/integrations/openclaw/apex-stock-assistant \
  /volume1/docker/openclaw/data/workspace/skills/
chmod 750 /volume1/docker/openclaw/data/workspace/skills/apex-stock-assistant/scripts/*.sh
chown -R 1000:1000 /volume1/docker/openclaw/data
chmod 600 /volume1/docker/openclaw/.env
vim /volume1/docker/openclaw/.env
```

在 `.env` 中填写：

- `OPENCLAW_GATEWAY_TOKEN`：执行 `openssl rand -hex 32` 生成。
- `APEX_BOT_CLIENT_KEY`：与 Apex `.env.production` 中的值完全一致。
- `APEX_BOT_CLIENT_SECRET`：执行 `openssl rand -hex 32` 生成，并与 Apex
  `.env.production` 中的值完全一致。
- `APEX_DOCKER_NETWORK`：Apex Compose 网络，默认 `apex_default`。
- `APEX_BOT_BASE_URL`：默认通过 Docker 网络直连
  `http://apex-backend-1:8080`，不经过公网域名或 NAS Tailscale 地址。

不要把真实密钥提交到 Git，也不要在聊天或终端截图中展示。

## 2. 配置 Apex

编辑 Apex 已有的生产配置，不要从模板覆盖：

```bash
vim /volume1/docker/apex/.env.production
```

第一阶段只开启问答接口，主动微信推送保持关闭：

```dotenv
APEX_BOT_ENABLED=true
APEX_BOT_CLIENT_KEY=与OpenClaw一致
APEX_BOT_CLIENT_SECRET=与OpenClaw一致
APEX_BOT_WECLAW_ENABLED=false
```

之后由你按现有流程重建 Apex 后端。

## 3. OpenClaw 首次初始化

以下命令使用群晖 Container Manager 自带的 Docker：

```bash
cd /volume1/docker/openclaw
DOCKER=/var/packages/ContainerManager/target/usr/bin/docker
$DOCKER compose --env-file .env -f compose.yaml pull
$DOCKER compose --env-file .env -f compose.yaml run --rm --no-deps \
  --entrypoint node openclaw-gateway \
  dist/index.js onboard --mode local --no-install-daemon
$DOCKER compose --env-file .env -f compose.yaml run --rm --no-deps \
  --entrypoint node openclaw-gateway \
  dist/index.js config set --batch-json \
  '[{"path":"gateway.mode","value":"local"},{"path":"gateway.bind","value":"lan"},{"path":"gateway.controlUi.allowedOrigins","value":["http://localhost:18789","http://127.0.0.1:18789"]}]'
$DOCKER compose --env-file .env -f compose.yaml up -d openclaw-gateway
$DOCKER compose --env-file .env -f compose.yaml ps
```

Onboarding 会要求选择模型供应商并填写对应 API Key。使用你已有的供应商即可；
该选择与 Apex 自己使用的 Kimi 配置相互独立。

默认镜像地址为：

```dotenv
OPENCLAW_IMAGE=registry.cn-hangzhou.aliyuncs.com/awe-images/openclaw:2026.7.1-2
```

这是固定版本标签，不要改成 `latest`。如果仓库改为私有，需要先在 NAS 执行
`docker login registry.cn-hangzhou.aliyuncs.com`，再执行拉取命令。

## 4. 从 Mac 打开控制台

保持下面的 SSH 隧道运行：

```bash
ssh -N -L 18789:127.0.0.1:18789 apex-nas
```

浏览器访问 `http://127.0.0.1:18789/`，填入 `.env` 中的 Gateway Token。

## 5. 验证 Apex Skill

在 NAS root 会话中执行：

```bash
cd /volume1/docker/openclaw
DOCKER=/var/packages/ContainerManager/target/usr/bin/docker
$DOCKER compose --env-file .env -f compose.yaml run --rm openclaw-cli \
  agent --agent main --message "请问 Apex：宁德时代现在风险大吗？"
$DOCKER compose --env-file .env -f compose.yaml run --rm openclaw-cli \
  agent --agent main --message "我今天亏多少"
$DOCKER compose --env-file .env -f compose.yaml run --rm openclaw-cli \
  agent --agent main --message "针对疯锅的持仓，你有什么投资建议？"
```

官方镜像已包含 Skill 所需的 Node.js、`curl` 和 `openssl`。如果命令执行失败，先看
Gateway 日志和 Apex 返回的鉴权错误，不要在运行中的容器里临时安装软件。

更新 Apex 代码中的 Skill 后，需要同步到已运行的 OpenClaw 工作目录并重启
Gateway，旧副本不会自动更新。NAS 上可直接运行部署脚本：

```bash
/volume1/docker/apex/integrations/openclaw/deployment/deploy-openclaw-skill.sh
```

脚本会请求 `sudo` 权限，原子替换 Skill、固定容器用户权限、强制重建 Gateway，并
输出服务状态和最近日志。支持用 `APEX_DIR`、`OPENCLAW_DIR`、`DOCKER` 和
`SKILL_NAME` 覆盖默认路径。重启后确认新工具脚本已生效：

```bash
test -x /volume1/docker/openclaw/data/workspace/skills/apex-stock-assistant/scripts/apex_tool.sh
```

## 6. 微信通道边界

OpenClaw 和 Apex 问答验证通过后，再选择个人微信 Channel。不同插件的登录方式、
主动发送接口和收件人 ID 不兼容，因此当前不假设 WeClaw，也不启用 Apex 的
`/api/send` 客户端。确定实际微信插件后，再适配主动推送并配置真实收件人 ID。

## 7. 日常操作

```bash
cd /volume1/docker/openclaw
DOCKER=/var/packages/ContainerManager/target/usr/bin/docker
$DOCKER compose --env-file .env -f compose.yaml ps
$DOCKER compose --env-file .env -f compose.yaml logs --tail=200 openclaw-gateway
$DOCKER compose --env-file .env -f compose.yaml pull
$DOCKER compose --env-file .env -f compose.yaml up -d openclaw-gateway
```

升级前将 `.env` 的镜像标签改为已确认的新版本，不要改成 `latest`。状态和 Skill
均位于 `/volume1/docker/openclaw/data`，重建容器不会删除它们。
