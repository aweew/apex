#!/usr/bin/env bash

set -euo pipefail

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly deploy_script="${script_dir}/deploy-openclaw.sh"
readonly test_dir="$(mktemp -d "${TMPDIR:-/tmp}/openclaw-deploy-test.XXXXXX")"
readonly mock_bin="${test_dir}/bin"
readonly apex_dir="${test_dir}/apex"
readonly openclaw_dir="${test_dir}/openclaw"
readonly command_path="${test_dir}/global-bin/deploy-openclaw.sh"
readonly command_alias_path="${test_dir}/path-bin/deploy-openclaw.sh"
readonly docker_log="${test_dir}/docker.log"
readonly git_log="${test_dir}/git.log"

cleanup() {
    if [[ "${KEEP_TEST_DIR:-false}" == "true" ]]; then
        echo "测试目录保留在 ${test_dir}" >&2
        return
    fi
    rm -rf "${test_dir}"
}

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

trap cleanup EXIT INT TERM

[[ -f "${deploy_script}" ]] || fail "部署脚本不存在"

mkdir -p "${mock_bin}" "${apex_dir}/integrations/openclaw/deployment" \
    "${apex_dir}/integrations/openclaw/apex-stock-assistant/scripts" \
    "${openclaw_dir}" "$(dirname "${command_path}")" "$(dirname "${command_alias_path}")"
cp "${script_dir}/compose.yaml" "${apex_dir}/integrations/openclaw/deployment/compose.yaml"
cp "${script_dir}/compose.yaml" "${openclaw_dir}/compose.yaml"
cp "${deploy_script}" "${apex_dir}/integrations/openclaw/deployment/deploy-openclaw.sh"
cp "${script_dir}/../apex-stock-assistant/SKILL.md" \
    "${apex_dir}/integrations/openclaw/apex-stock-assistant/SKILL.md"
cp "${script_dir}/../apex-stock-assistant/scripts/"*.sh \
    "${apex_dir}/integrations/openclaw/apex-stock-assistant/scripts/"

cat >"${mock_bin}/sudo" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == "-n" ]] && shift
exec "$@"
EOF
chmod +x "${mock_bin}/sudo"

cat >"${mock_bin}/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${GIT_CALL_LOG}"
EOF
chmod +x "${mock_bin}/git"

cat >"${mock_bin}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${DOCKER_CALL_LOG}"

case "$*" in
    "info"|"compose version")
        exit 0
        ;;
    *"/config/.env:ro"*)
        if [[ "${MOCK_ENV_CHECK:-valid}" == "missing" ]]; then
            echo "OpenClaw 配置缺少必填项: APEX_BOT_CLIENT_SECRET" >&2
            exit 1
        fi
        exit 0
        ;;
    *"compose --env-file "*" config --quiet")
        exit 0
        ;;
    *"compose --env-file "*" ps -q openclaw-gateway")
        echo "gateway-id"
        ;;
    *"compose --env-file "*" ps openclaw-gateway")
        echo "openclaw-gateway healthy"
        ;;
    "inspect --format "*" gateway-id")
        echo "healthy"
        ;;
    "exec gateway-id /bin/bash "*"apex_ask.sh"*)
        echo '{"code":0,"data":{"intent":"TODAY_DECISION","dataLevel":"GREEN","answer":"敏感的完整研判正文"}}'
        ;;
    "exec -i gateway-id node -e "*)
        cat >/dev/null
        echo "code=0 intent=TODAY_DECISION dataLevel=GREEN"
        ;;
esac
EOF
chmod +x "${mock_bin}/docker"

cat >"${openclaw_dir}/.env" <<'EOF'
OPENCLAW_GATEWAY_TOKEN=gateway-token-for-test
APEX_BOT_CLIENT_KEY=client-key-for-test
APEX_BOT_CLIENT_SECRET=client-secret-for-test
APEX_BOT_EXTERNAL_USER_ID=wx-user-for-test
EOF

run_deploy() {
    PATH="${mock_bin}:${PATH}" \
        APEX_DIR="${apex_dir}" \
        OPENCLAW_DIR="${openclaw_dir}" \
        DOCKER="${mock_bin}/docker" \
        OPENCLAW_COMMAND_PATH="${OPENCLAW_COMMAND_PATH:-${command_path}}" \
        OPENCLAW_COMMAND_ALIAS_PATH="${OPENCLAW_COMMAND_ALIAS_PATH:-${command_alias_path}}" \
        DOCKER_CALL_LOG="${docker_log}" \
        GIT_CALL_LOG="${git_log}" \
        MOCK_ENV_CHECK="${MOCK_ENV_CHECK:-valid}" \
        bash "${deploy_script}" "$@"
}

run_deploy --help | grep -q -- '--install-command' \
    || fail "帮助中缺少全局命令安装选项"
run_deploy --help | grep -q -- '--check' || fail "帮助中缺少只读检查选项"
if run_deploy --unsupported >"${test_dir}/unsupported.log" 2>&1; then
    fail "未知参数未被拒绝"
fi
grep -q '不支持的参数' "${test_dir}/unsupported.log" || fail "未知参数错误不明确"

run_deploy --install-command >"${test_dir}/install.log" 2>&1 \
    || fail "全局命令安装失败"
[[ -x "${command_path}" ]] || fail "全局命令不可执行"
[[ -x "${command_alias_path}" ]] || fail "PATH 内的全局命令入口不可执行"
grep -q '^# Apex OpenClaw deployment command$' "${command_path}" \
    || fail "全局命令缺少归属标记"
grep -q "${apex_dir}/integrations/openclaw/deployment/deploy-openclaw.sh" "${command_path}" \
    || fail "全局命令没有委托到仓库脚本"
run_deploy --install-command >"${test_dir}/reinstall.log" 2>&1 \
    || fail "全局命令无法幂等更新"

(
    cd "${test_dir}"
    PATH="$(dirname "${command_alias_path}"):${PATH}" deploy-openclaw.sh --help | grep -q -- '--check'
) || fail "全局命令无法在仓库外执行"

readonly foreign_command="${test_dir}/global-bin/foreign-openclaw"
echo '# foreign command' >"${foreign_command}"
if OPENCLAW_COMMAND_PATH="${foreign_command}" run_deploy --install-command \
    >"${test_dir}/foreign.log" 2>&1; then
    fail "安装器覆盖了无关命令"
fi
grep -q '^# foreign command$' "${foreign_command}" || fail "无关命令内容被改动"

mv "${openclaw_dir}/.env" "${test_dir}/openclaw.env"
if run_deploy --check >"${test_dir}/missing-env.log" 2>&1; then
    fail "缺少 .env 时检查仍然成功"
fi
[[ ! -e "${openclaw_dir}/.env" ]] || fail "部署脚本自动创建或覆盖了 .env"
grep -q '不会自动创建或覆盖' "${test_dir}/missing-env.log" \
    || fail "缺少 .env 的错误不可操作"
mv "${test_dir}/openclaw.env" "${openclaw_dir}/.env"

if MOCK_ENV_CHECK=missing run_deploy --check >"${test_dir}/missing-key.log" 2>&1; then
    fail "缺少必填变量时检查仍然成功"
fi
grep -q 'APEX_BOT_CLIENT_SECRET' "${test_dir}/missing-key.log" \
    || fail "缺少变量名称未报告"
if grep -q 'client-secret-for-test' "${test_dir}/missing-key.log"; then
    fail "检查输出泄露了变量值"
fi

: >"${docker_log}"
: >"${git_log}"
run_deploy >"${test_dir}/deploy.log" 2>&1 || fail "默认一键部署失败"
grep -q -- "-C ${apex_dir} pull --ff-only" "${git_log}" \
    || fail "默认部署没有用 ff-only 拉取最新代码"
grep -q 'registry.cn-hangzhou.aliyuncs.com/awe-images/openclaw:2026.7.1-2' "${docker_log}" \
    || fail "没有使用固定 OpenClaw 镜像"
grep -q '/source/deployment:ro' "${docker_log}" || fail "Compose 源目录没有只读挂载"
grep -q '/source/skill:ro' "${docker_log}" || fail "Skill 源目录没有只读挂载"
grep -q '\.compose.yaml.stage' "${docker_log}" || fail "Compose 未使用临时文件原子替换"
grep -q 'skill_stage=.*\.stage' "${docker_log}" || fail "Skill 未使用临时目录原子替换"
grep -q 'find .*skill_stage.* -type d .*chmod 750' "${docker_log}" \
    || fail "Skill 目录没有显式修正为可访问权限"
grep -q 'pull registry.cn-hangzhou.aliyuncs.com/awe-images/openclaw:2026.7.1-2' "${docker_log}" \
    || fail "没有拉取固定 OpenClaw 镜像"
grep -q 'up -d --force-recreate openclaw-gateway' "${docker_log}" \
    || fail "Gateway 没有强制重建"
grep -q 'apex_ask.sh 今天怎么操作？' "${docker_log}" \
    || fail "没有执行只读今日操作探测"
grep -q 'code=0 intent=TODAY_DECISION dataLevel=GREEN' "${test_dir}/deploy.log" \
    || fail "部署结果没有输出安全的探测摘要"
if grep -q '敏感的完整研判正文' "${test_dir}/deploy.log"; then
    fail "部署输出泄露了完整金融问答内容"
fi

echo "PASS: deploy-openclaw.sh"
