#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$SCRIPT_DIR/docker-compose.prod.yml" ]; then
    PROJECT_DIR=$SCRIPT_DIR
elif [ -f "$SCRIPT_DIR/../docker-compose.prod.yml" ]; then
    PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
else
    echo "[Apex] 错误: 无法从 $SCRIPT_DIR 定位 docker-compose.prod.yml" >&2
    exit 1
fi
ENV_FILE=${APEX_ENV_FILE:-"$PROJECT_DIR/.env.production"}
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"
MODE=update
DEPLOY_TARGET=all

log() {
    echo "[Apex 日志] $*"
}

fail() {
    echo "[Apex] 错误: $*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
用法:
  sh scripts/deploy-nas.sh            拉取最新代码后部署
  sh scripts/deploy-nas.sh --be       只部署后端
  sh scripts/deploy-nas.sh --fe       只部署前端
  sh scripts/deploy-nas.sh --update   拉取最新代码后部署（兼容参数）
  sh scripts/deploy-nas.sh --check    仅检查配置和依赖
  sh scripts/deploy-nas.sh --install-command
                                      安装全局 deploy-nas.sh 命令
  sh scripts/deploy-nas.sh --help     显示帮助
EOF
}

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

read_env() {
    env_key=$1
    sed -n "s/^${env_key}=//p" "$ENV_FILE" | tail -n 1
}

require_env() {
    env_key=$1
    env_value=$(read_env "$env_key")
    [ -n "$env_value" ] || fail "$ENV_FILE 缺少 ${env_key}"
}

show_logs() {
    log "最近的容器日志:"
    case "$DEPLOY_TARGET" in
        backend)
            compose logs --tail=120 backend 2>&1 || true
            ;;
        frontend)
            compose logs --tail=120 frontend 2>&1 || true
            ;;
        *)
            compose logs --tail=120 backend frontend 2>&1 || true
            ;;
    esac
}

wait_for_backend() {
    backend_id=$(compose ps -q backend)
    [ -n "$backend_id" ] || {
        show_logs
        fail "未找到 backend 容器"
    }

    elapsed=0
    timeout=240
    while [ "$elapsed" -lt "$timeout" ]; do
        backend_status=$(docker inspect \
            --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
            "$backend_id" 2>/dev/null || true)
        case "$backend_status" in
            healthy)
                log "后端健康检查通过"
                return 0
                ;;
            unhealthy|exited|dead)
                show_logs
                fail "后端状态为 ${backend_status}"
                ;;
        esac
        sleep 5
        elapsed=$((elapsed + 5))
    done

    show_logs
    fail "等待后端健康检查超时 (${timeout}s)"
}

wait_for_frontend() {
    elapsed=0
    timeout=60
    while [ "$elapsed" -lt "$timeout" ]; do
        frontend_id=$(compose ps -q frontend)
        if [ -n "$frontend_id" ]; then
            frontend_status=$(docker inspect --format '{{.State.Status}}' "$frontend_id" 2>/dev/null || true)
            if [ "$frontend_status" = "running" ]; then
                log "前端容器已运行"
                return 0
            fi
            if [ "$frontend_status" = "exited" ] || [ "$frontend_status" = "dead" ]; then
                show_logs
                fail "前端状态为 ${frontend_status}"
            fi
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    show_logs
    fail "等待前端容器启动超时 (${timeout}s)"
}

install_global_command() {
    command_path=${APEX_COMMAND_PATH:-/usr/local/bin/deploy-nas.sh}
    command_dir=$(dirname "$command_path")
    [ -d "$command_dir" ] || fail "全局命令目录不存在: $command_dir"
    [ -w "$command_dir" ] || fail "全局命令目录不可写，请使用 root 执行: $command_dir"

    if [ -e "$command_path" ] && ! grep -q '^# Apex NAS deployment command$' "$command_path"; then
        fail "全局命令已存在且不属于 Apex: $command_path"
    fi

    deploy_script="$PROJECT_DIR/scripts/deploy-nas.sh"
    if [ ! -f "$deploy_script" ]; then
        deploy_script="$SCRIPT_DIR/deploy-nas.sh"
    fi

    command_tmp="${command_path}.tmp.$$"
    trap 'rm -f "$command_tmp"' 0 1 2 15
    cat >"$command_tmp" <<EOF
#!/bin/sh
# Apex NAS deployment command
exec /bin/sh "$deploy_script" "\$@"
EOF
    chmod 755 "$command_tmp"
    mv "$command_tmp" "$command_path"
    trap - 0 1 2 15

    log "全局命令安装成功: $command_path"
}

case "${1:-}" in
    "")
        ;;
    --update)
        MODE=update
        ;;
    --be)
        DEPLOY_TARGET=backend
        ;;
    --fe)
        DEPLOY_TARGET=frontend
        ;;
    --check)
        MODE=check
        ;;
    --install-command)
        MODE=install
        ;;
    --help|-h)
        usage
        exit 0
        ;;
    *)
        usage >&2
        fail "不支持的参数: $1"
        ;;
esac

[ "$#" -le 1 ] || fail "只能指定一个参数"
[ -f "$COMPOSE_FILE" ] || fail "未找到 $COMPOSE_FILE"

if [ "$MODE" = "install" ]; then
    install_global_command
    exit 0
fi

if [ "$MODE" = "update" ]; then
    command -v git >/dev/null 2>&1 || fail "未安装 git"
    log "拉取最新代码"
    git -C "$PROJECT_DIR" pull --ff-only
fi

[ -f "$ENV_FILE" ] || fail "未找到生产配置 ${ENV_FILE}，脚本不会自动创建或覆盖该文件"

if grep -q '=change_me' "$ENV_FILE"; then
    fail "$ENV_FILE 仍包含 change_me，请先替换所有占位密钥"
fi

require_env MYSQL_HOST
require_env MYSQL_USER
require_env MYSQL_PASSWORD
require_env REDIS_PASSWORD
require_env APEX_JWT_SECRET

jwt_secret=$(read_env APEX_JWT_SECRET)
[ "${#jwt_secret}" -ge 32 ] || fail "APEX_JWT_SECRET 至少需要 32 个字符"

command -v docker >/dev/null 2>&1 || fail "未安装 docker"
docker info >/dev/null 2>&1 || fail "Docker daemon 未运行或当前用户无访问权限"
docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose v2"

mysql_network=$(read_env MYSQL_DOCKER_NETWORK)
mysql_network=${mysql_network:-mysql_default}
docker network inspect "$mysql_network" >/dev/null 2>&1 \
    || fail "MySQL Docker 网络不存在: $mysql_network"

compose config --quiet || fail "生产 Compose 配置无效"
log "配置与依赖检查通过"

if [ "$MODE" = "check" ]; then
    exit 0
fi

log "构建并启动生产服务: $DEPLOY_TARGET"
case "$DEPLOY_TARGET" in
    backend)
        compose up -d --build --no-deps backend || {
            show_logs
            fail "Docker Compose 启动失败"
        }
        ;;
    frontend)
        compose up -d --build --no-deps frontend || {
            show_logs
            fail "Docker Compose 启动失败"
        }
        ;;
    *)
        compose up -d --build || {
            show_logs
            fail "Docker Compose 启动失败"
        }
        ;;
esac

case "$DEPLOY_TARGET" in
    backend)
        wait_for_backend
        ;;
    frontend)
        wait_for_frontend
        ;;
    *)
        wait_for_backend
        wait_for_frontend
        ;;
esac

if [ "$DEPLOY_TARGET" = "backend" ]; then
    log "部署成功: backend"
    exit 0
fi

command -v curl >/dev/null 2>&1 || fail "未安装 curl，无法执行部署后验证"
http_port=$(read_env APEX_HTTP_PORT)
http_port=${http_port:-8088}
if [ "$DEPLOY_TARGET" = "frontend" ]; then
    health_url="http://127.0.0.1:${http_port}/"
else
    health_url="http://127.0.0.1:${http_port}/apex/api/health"
fi

if ! curl --noproxy '*' --fail --silent --show-error --max-time 15 "$health_url" >/dev/null; then
    show_logs
    fail "容器已启动，但无法通过宿主机访问 $health_url"
fi

log "部署成功"
log "内网入口: http://127.0.0.1:${http_port}/"
log "DSM 反向代理目的地: http://127.0.0.1:${http_port}"
