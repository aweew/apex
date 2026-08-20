#!/usr/bin/env bash

set -euo pipefail

readonly openclaw_image="registry.cn-hangzhou.aliyuncs.com/awe-images/openclaw:2026.7.1-2"
readonly skill_name="apex-stock-assistant"
readonly apex_dir="${APEX_DIR:-/volume1/docker/apex}"
readonly openclaw_dir="${OPENCLAW_DIR:-/volume1/docker/openclaw}"
readonly docker_bin="${DOCKER:-/usr/local/bin/docker}"
readonly command_path="${OPENCLAW_COMMAND_PATH:-/usr/local/bin/deploy-openclaw.sh}"
readonly command_alias_path="${OPENCLAW_COMMAND_ALIAS_PATH:-/usr/bin/deploy-openclaw.sh}"
readonly source_deployment_dir="${apex_dir}/integrations/openclaw/deployment"
readonly source_skill_dir="${apex_dir}/integrations/openclaw/${skill_name}"
readonly deploy_script="${source_deployment_dir}/deploy-openclaw.sh"
readonly compose_file="${openclaw_dir}/compose.yaml"
readonly env_file="${openclaw_dir}/.env"

mode="deploy"

log() {
    echo "[OpenClaw] $*"
}

fail() {
    echo "[OpenClaw] 错误: $*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
用法:
  deploy-openclaw.sh                    拉取代码并完成 OpenClaw 一键部署
  deploy-openclaw.sh --check            只读检查配置、依赖和 Gateway 状态
  deploy-openclaw.sh --install-command  安装或更新全局命令
  deploy-openclaw.sh --help             显示帮助
EOF
}

run_docker() {
    sudo -n "${docker_bin}" "$@"
}

compose() {
    run_docker compose --env-file "${env_file}" -f "${compose_file}" "$@"
}

require_source_files() {
    [[ -f "${source_deployment_dir}/compose.yaml" ]] \
        || fail "缺少 Compose 源文件: ${source_deployment_dir}/compose.yaml"
    [[ -f "${source_skill_dir}/SKILL.md" ]] \
        || fail "缺少 Skill 定义: ${source_skill_dir}/SKILL.md"

    local script_name
    for script_name in apex_ask.sh apex_tool.sh apex_trade_event.sh; do
        [[ -f "${source_skill_dir}/scripts/${script_name}" ]] \
            || fail "缺少 Skill 脚本: ${source_skill_dir}/scripts/${script_name}"
    done

    grep -q 'APEX_BOT_EXTERNAL_USER_ID' "${source_skill_dir}/scripts/apex_ask.sh" \
        || fail "问答脚本未绑定外部用户"
}

require_docker() {
    command -v sudo >/dev/null 2>&1 || fail "未安装 sudo"
    [[ -x "${docker_bin}" ]] || fail "未找到 Docker: ${docker_bin}"
    run_docker info >/dev/null 2>&1 \
        || fail "当前用户不能免密运行 sudo -n ${docker_bin}"
    run_docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose v2"
}

validate_env() {
    run_docker run --rm --network none --user 0:0 \
        -v "${env_file}:/config/.env:ro" \
        "${openclaw_image}" /bin/sh -eu -c '
env_file=/config/.env
missing_keys=""
placeholder_keys=""
for env_key in OPENCLAW_GATEWAY_TOKEN APEX_BOT_CLIENT_KEY APEX_BOT_CLIENT_SECRET APEX_BOT_EXTERNAL_USER_ID; do
    env_value=$(awk -F= -v key="${env_key}" '\''$1 == key {sub(/^[^=]*=/, ""); value=$0} END {print value}'\'' "${env_file}")
    if [ -z "${env_value}" ]; then
        missing_keys="${missing_keys} ${env_key}"
    else
        case "${env_value}" in
            change_me*) placeholder_keys="${placeholder_keys} ${env_key}" ;;
        esac
    fi
done
if [ -n "${missing_keys}" ]; then
    echo "OpenClaw 配置缺少必填项:${missing_keys}" >&2
    exit 1
fi
if [ -n "${placeholder_keys}" ]; then
    echo "OpenClaw 配置仍包含占位值:${placeholder_keys}" >&2
    exit 1
fi
' || fail "OpenClaw .env 校验失败，脚本未显示任何密钥值"
}

sync_runtime_files() {
    local stage_suffix="deploy.$$"

    run_docker run --rm --network none --user 0:0 \
        -e "DEPLOY_STAGE_SUFFIX=${stage_suffix}" \
        -e "SKILL_NAME=${skill_name}" \
        -v "${source_deployment_dir}:/source/deployment:ro" \
        -v "${source_skill_dir}:/source/skill:ro" \
        -v "${openclaw_dir}:/target" \
        "${openclaw_image}" /bin/sh -eu -c '
compose_stage="/target/.compose.yaml.stage.${DEPLOY_STAGE_SUFFIX}"
skills_dir=/target/data/workspace/skills
skill_target="${skills_dir}/${SKILL_NAME}"
skill_stage="${skills_dir}/.${SKILL_NAME}.stage.${DEPLOY_STAGE_SUFFIX}"
skill_backup="${skills_dir}/.${SKILL_NAME}.backup.${DEPLOY_STAGE_SUFFIX}"

cleanup() {
    rm -f "${compose_stage}"
    rm -rf "${skill_stage}"
}
trap cleanup EXIT INT TERM

mkdir -p "${skills_dir}"
cp /source/deployment/compose.yaml "${compose_stage}"
chmod 644 "${compose_stage}"
mkdir "${skill_stage}"
cp -R /source/skill/. "${skill_stage}/"
chown -R 1000:1000 "${skill_stage}"
find "${skill_stage}" -type d -exec chmod 750 {} \;
find "${skill_stage}" -type f -exec chmod 640 {} \;
find "${skill_stage}/scripts" -type f -name "*.sh" -exec chmod 750 {} \;

rm -rf "${skill_backup}"
if [ -e "${skill_target}" ]; then
    mv "${skill_target}" "${skill_backup}"
fi
if ! mv "${skill_stage}" "${skill_target}"; then
    if [ -e "${skill_backup}" ]; then
        mv "${skill_backup}" "${skill_target}"
    fi
    exit 1
fi
mv "${compose_stage}" /target/compose.yaml
rm -rf "${skill_backup}"
trap - EXIT INT TERM
' || fail "同步 Compose 或 Apex Skill 失败"

    log "已同步 Compose 与 Apex Skill"
}

show_gateway_logs() {
    compose logs --tail=120 openclaw-gateway 2>&1 || true
}

wait_for_gateway() {
    local gateway_id
    local gateway_status
    local elapsed=0
    local timeout=180

    gateway_id="$(compose ps -q openclaw-gateway)"
    if [[ -z "${gateway_id}" ]]; then
        show_gateway_logs
        fail "未找到 OpenClaw Gateway 容器"
    fi

    while (( elapsed < timeout )); do
        gateway_status="$(run_docker inspect \
            --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
            "${gateway_id}" 2>/dev/null || true)"
        case "${gateway_status}" in
            healthy)
                log "Gateway 健康检查通过"
                return 0
                ;;
            unhealthy|exited|dead)
                show_gateway_logs
                fail "Gateway 状态为 ${gateway_status}"
                ;;
        esac
        sleep 5
        elapsed=$((elapsed + 5))
    done

    show_gateway_logs
    fail "等待 Gateway 健康检查超时 (${timeout}s)"
}

probe_today_decision() {
    local gateway_id
    local probe_response
    local probe_summary

    gateway_id="$(compose ps -q openclaw-gateway)"
    [[ -n "${gateway_id}" ]] || fail "未找到 OpenClaw Gateway 容器"

    probe_response="$(run_docker exec "${gateway_id}" /bin/bash \
        "/home/node/.openclaw/workspace/skills/${skill_name}/scripts/apex_ask.sh" \
        "今天怎么操作？")" || {
        show_gateway_logs
        fail "今日操作只读探测请求失败"
    }

    probe_summary="$(printf '%s' "${probe_response}" | run_docker exec -i "${gateway_id}" node -e '
let responseBody = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => responseBody += chunk);
process.stdin.on("end", () => {
    const response = JSON.parse(responseBody);
    const result = response && response.data ? response.data : {};
    const validLevels = ["GREEN", "YELLOW", "RED"];
    if (response.code !== 0 || result.intent !== "TODAY_DECISION" || !validLevels.includes(result.dataLevel)) {
        process.exit(1);
    }
    process.stdout.write(`code=${response.code} intent=${result.intent} dataLevel=${result.dataLevel}`);
});
')" || fail "今日操作探测响应不符合 Apex 契约"

    log "只读探测通过: ${probe_summary}"
}

write_global_wrapper() {
    local target_path="$1"
    local temporary_path="${target_path}.tmp.$$"

    cat >"${temporary_path}" <<EOF
#!/usr/bin/env bash
# Apex OpenClaw deployment command
exec /bin/bash "${deploy_script}" "\$@"
EOF
    chmod 755 "${temporary_path}"
    mv "${temporary_path}" "${target_path}"
}

install_command_at_path() {
    local target_path="$1"
    local command_dir
    local command_name

    command_dir="$(dirname "${target_path}")"
    command_name="$(basename "${target_path}")"
    [[ -d "${command_dir}" ]] || fail "全局命令目录不存在: ${command_dir}"

    if [[ -e "${target_path}" ]] \
        && ! grep -q '^# Apex OpenClaw deployment command$' "${target_path}" 2>/dev/null; then
        fail "全局命令已存在且不属于 Apex: ${target_path}"
    fi

    if [[ -w "${command_dir}" ]]; then
        write_global_wrapper "${target_path}"
    else
        require_docker
        run_docker run --rm --network none --user 0:0 \
            -e "COMMAND_NAME=${command_name}" \
            -e "DEPLOY_SCRIPT=${deploy_script}" \
            -v "${command_dir}:/command" \
            "${openclaw_image}" /bin/sh -eu -c '
target_path="/command/${COMMAND_NAME}"
temporary_path="${target_path}.tmp.$$"
if [ -e "${target_path}" ] && ! grep -q "^# Apex OpenClaw deployment command$" "${target_path}"; then
    echo "全局命令已存在且不属于 Apex: ${target_path}" >&2
    exit 1
fi
cleanup() {
    rm -f "${temporary_path}"
}
trap cleanup EXIT INT TERM
{
    echo "#!/usr/bin/env bash"
    echo "# Apex OpenClaw deployment command"
    printf '\''exec /bin/bash "%s" "$@"\n'\'' "${DEPLOY_SCRIPT}"
} >"${temporary_path}"
chmod 755 "${temporary_path}"
mv "${temporary_path}" "${target_path}"
trap - EXIT INT TERM
' || fail "安装全局命令失败"
    fi
}

install_global_command() {
    [[ -f "${deploy_script}" ]] || fail "仓库部署脚本不存在: ${deploy_script}"

    install_command_at_path "${command_path}"
    if [[ "${command_alias_path}" != "${command_path}" ]]; then
        install_command_at_path "${command_alias_path}"
    fi

    log "全局命令已安装: ${command_path}"
    if [[ "${command_alias_path}" != "${command_path}" ]]; then
        log "PATH 命令入口已安装: ${command_alias_path}"
    fi
}

case "${1:-}" in
    "")
        ;;
    --check)
        mode="check"
        ;;
    --install-command)
        mode="install"
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

[[ "$#" -le 1 ]] || fail "只能指定一个参数"

if [[ "${mode}" == "install" ]]; then
    install_global_command
    exit 0
fi

if [[ "${mode}" == "deploy" ]]; then
    command -v git >/dev/null 2>&1 || fail "未安装 git"
    log "拉取 Apex 最新代码"
    git -C "${apex_dir}" pull --ff-only
fi

require_source_files
[[ -f "${env_file}" ]] \
    || fail "未找到 OpenClaw 配置 ${env_file}，脚本不会自动创建或覆盖该文件"
require_docker

if [[ "${mode}" == "check" ]]; then
    [[ -f "${compose_file}" ]] || fail "未找到 OpenClaw Compose: ${compose_file}"
    run_docker image inspect "${openclaw_image}" >/dev/null 2>&1 \
        || fail "固定 OpenClaw 镜像尚未拉取，请直接运行 deploy-openclaw.sh"
else
    log "拉取固定 OpenClaw 镜像"
    run_docker pull "${openclaw_image}"
fi

validate_env

if [[ "${mode}" == "check" ]]; then
    compose config --quiet || fail "OpenClaw Compose 配置无效"
    log "配置与依赖检查通过"
    compose ps openclaw-gateway
    exit 0
fi

sync_runtime_files
compose config --quiet || fail "同步后的 OpenClaw Compose 配置无效"

log "强制重建 Gateway"
compose up -d --force-recreate openclaw-gateway || {
    show_gateway_logs
    fail "Gateway 重建失败"
}

wait_for_gateway
probe_today_decision
compose ps openclaw-gateway
log "OpenClaw 部署完成"
