#!/usr/bin/env bash

set -euo pipefail

readonly skill_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly skill_file="${skill_dir}/SKILL.md"
readonly ask_script="${skill_dir}/scripts/apex_ask.sh"

grep -q 'APEX_BOT_EXTERNAL_USER_ID' "${ask_script}"
grep -q 'userId: process.argv\[2\]' "${ask_script}"
grep -q '今天怎么操作' "${skill_file}"
grep -q 'does not explicitly name a portfolio' "${skill_file}"

if grep -q 'portfolioName":"疯锅' "${skill_file}"; then
    echo "Skill 不应将示例组合名当成默认值" >&2
    exit 1
fi

echo "Apex OpenClaw Skill 契约检查通过"
