#!/usr/bin/env bash

set -euo pipefail

readonly servlet_path="/apex/bot/v1/ask"
readonly http_method="POST"

if [[ $# -eq 0 || -z "${*//[[:space:]]/}" ]]; then
    echo "用法：apex_ask.sh <问题>" >&2
    exit 2
fi

for command_name in curl node openssl; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "缺少必需命令：${command_name}" >&2
        exit 3
    fi
done

for variable_name in APEX_BOT_BASE_URL APEX_BOT_CLIENT_KEY APEX_BOT_CLIENT_SECRET APEX_BOT_EXTERNAL_USER_ID; do
    if [[ -z "${!variable_name:-}" ]]; then
        echo "缺少必需环境变量：${variable_name}" >&2
        exit 4
    fi
done

readonly question="$*"
readonly request_body="$(node -e 'process.stdout.write(JSON.stringify({question: process.argv[1], userId: process.argv[2]}))' "${question}" "${APEX_BOT_EXTERNAL_USER_ID}")"
readonly timestamp="$(date +%s)"
readonly nonce="$(openssl rand -hex 16)"
readonly content_sha256="$(printf '%s' "${request_body}" | openssl dgst -sha256 | awk '{print $NF}')"
readonly canonical="$(printf '%s\n%s\n%s\n%s\n%s' \
    "${http_method}" \
    "${servlet_path}" \
    "${timestamp}" \
    "${nonce}" \
    "${content_sha256}")"
readonly signature="$(printf '%s' "${canonical}" \
    | openssl dgst -sha256 -hmac "${APEX_BOT_CLIENT_SECRET}" \
    | awk '{print $NF}')"
readonly request_url="${APEX_BOT_BASE_URL%/}${servlet_path}"

curl --fail-with-body --silent --show-error \
    --request "${http_method}" \
    --header "Content-Type: application/json" \
    --header "X-Apex-Key: ${APEX_BOT_CLIENT_KEY}" \
    --header "X-Apex-Timestamp: ${timestamp}" \
    --header "X-Apex-Nonce: ${nonce}" \
    --header "X-Apex-Content-Sha256: ${content_sha256}" \
    --header "X-Apex-Signature: ${signature}" \
    --data-binary "${request_body}" \
    -- \
    "${request_url}"
