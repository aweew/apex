#!/usr/bin/env bash

set -euo pipefail

readonly skill_name="${SKILL_NAME:-apex-stock-assistant}"
readonly apex_dir="${APEX_DIR:-/volume1/docker/apex}"
readonly openclaw_dir="${OPENCLAW_DIR:-/volume1/docker/openclaw}"
readonly docker_bin="${DOCKER:-/var/packages/ContainerManager/target/usr/bin/docker}"
readonly source_dir="${apex_dir}/integrations/openclaw/${skill_name}"
readonly skills_dir="${openclaw_dir}/data/workspace/skills"
readonly target_dir="${skills_dir}/${skill_name}"
readonly compose_file="${openclaw_dir}/compose.yaml"
readonly env_file="${openclaw_dir}/.env"

if [[ "${EUID}" -ne 0 ]]; then
    exec sudo "${BASH_SOURCE[0]}" "$@"
fi

if [[ ! -f "${source_dir}/SKILL.md" || ! -x "${source_dir}/scripts/apex_tool.sh" ]]; then
    echo "技能源文件不完整：${source_dir}" >&2
    exit 1
fi
if [[ ! -x "${docker_bin}" ]]; then
    echo "未找到 Docker: ${docker_bin}" >&2
    exit 1
fi
if [[ ! -f "${compose_file}" || ! -r "${env_file}" ]]; then
    echo "OpenClaw 编排配置不可用：${openclaw_dir}" >&2
    exit 1
fi

mkdir -p "${skills_dir}"
stage_dir="$(mktemp -d "${skills_dir}/.${skill_name}.stage.XXXXXX")"
backup_dir=""

cleanup() {
    if [[ -n "${stage_dir}" && -d "${stage_dir}" ]]; then
        rm -rf "${stage_dir}"
    fi
}
trap cleanup EXIT

cp -RP "${source_dir}/." "${stage_dir}/"
chown -R 1000:1000 "${stage_dir}"
chmod 750 "${stage_dir}/scripts/"*.sh

if [[ -e "${target_dir}" ]]; then
    backup_dir="${skills_dir}/.${skill_name}.backup.$(date +%Y%m%d%H%M%S)"
    mv "${target_dir}" "${backup_dir}"
fi
mv "${stage_dir}" "${target_dir}"
stage_dir=""

echo "已同步技能：${target_dir}"
"${docker_bin}" compose --env-file "${env_file}" -f "${compose_file}" up -d --force-recreate openclaw-gateway

if [[ -n "${backup_dir}" ]]; then
    rm -rf "${backup_dir}"
fi

"${docker_bin}" compose --env-file "${env_file}" -f "${compose_file}" ps openclaw-gateway
"${docker_bin}" compose --env-file "${env_file}" -f "${compose_file}" logs --tail=100 openclaw-gateway
