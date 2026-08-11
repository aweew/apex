#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_SCRIPT="$SCRIPT_DIR/deploy-nas.sh"
TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/apex-deploy-test.XXXXXX")

cleanup() {
    rm -rf "$TEST_DIR"
}

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

trap cleanup EXIT INT TERM

sh "$DEPLOY_SCRIPT" --help | grep -q -- '--be' || fail "backend option is missing from help"
sh "$DEPLOY_SCRIPT" --help | grep -q -- '--fe' || fail "frontend option is missing from help"
sh "$DEPLOY_SCRIPT" --help | grep -q -- '--install-command' || fail "global command installer is missing from help"

GLOBAL_BIN="$TEST_DIR/global-bin"
GLOBAL_COMMAND="$GLOBAL_BIN/deploy-nas.sh"
mkdir -p "$GLOBAL_BIN"
APEX_COMMAND_PATH="$GLOBAL_COMMAND" sh "$DEPLOY_SCRIPT" --install-command \
    >"$TEST_DIR/install-command.log" 2>&1 \
    || fail "global command installation failed"
test -x "$GLOBAL_COMMAND" || fail "global command is not executable"
grep -q "$SCRIPT_DIR/deploy-nas.sh" "$GLOBAL_COMMAND" || fail "global command does not use the repository script"
APEX_COMMAND_PATH="$GLOBAL_COMMAND" sh "$DEPLOY_SCRIPT" --install-command \
    >"$TEST_DIR/reinstall-command.log" 2>&1 \
    || fail "global command cannot be updated idempotently"

FOREIGN_COMMAND="$GLOBAL_BIN/foreign-command"
echo '# foreign command' >"$FOREIGN_COMMAND"
if APEX_COMMAND_PATH="$FOREIGN_COMMAND" sh "$DEPLOY_SCRIPT" --install-command \
    >"$TEST_DIR/foreign-command.log" 2>&1; then
    fail "global command installer overwrote an unrelated command"
fi
grep -q '^# foreign command$' "$FOREIGN_COMMAND" || fail "unrelated command content was changed"

(
    cd "$TEST_DIR"
    PATH="$GLOBAL_BIN:$PATH" deploy-nas.sh --help | grep -q -- '--be'
) || fail "global command cannot run outside the repository"

GIT_MOCK_BIN="$TEST_DIR/git-bin"
GIT_PULL_MARKER="$TEST_DIR/git-pull.marker"
mkdir -p "$GIT_MOCK_BIN"
cat >"$GIT_MOCK_BIN/git" <<'EOF'
#!/bin/sh
touch "$GIT_PULL_MARKER"
exit 0
EOF
chmod +x "$GIT_MOCK_BIN/git"

DEFAULT_ENV="$TEST_DIR/default.env"
if PATH="$GIT_MOCK_BIN:$PATH" GIT_PULL_MARKER="$GIT_PULL_MARKER" \
    APEX_ENV_FILE="$DEFAULT_ENV" sh "$DEPLOY_SCRIPT" >"$TEST_DIR/default.log" 2>&1; then
    fail "default deployment should stop when production environment is missing"
fi
test -f "$GIT_PULL_MARKER" || fail "default deployment did not pull the latest code"

FIRST_ENV="$TEST_DIR/first.env"
if APEX_ENV_FILE="$FIRST_ENV" sh "$DEPLOY_SCRIPT" --check >"$TEST_DIR/first.log" 2>&1; then
    fail "missing production environment should be rejected"
fi
test ! -e "$FIRST_ENV" || fail "deployment script must not create the environment file"
grep -q '未找到生产配置' "$TEST_DIR/first.log" || fail "missing environment error is not actionable"

cp "$SCRIPT_DIR/../.env.production.example" "$FIRST_ENV"
if APEX_ENV_FILE="$FIRST_ENV" sh "$DEPLOY_SCRIPT" --check >"$TEST_DIR/placeholder.log" 2>&1; then
    fail "placeholder secrets should be rejected"
fi
grep -q 'change_me' "$TEST_DIR/placeholder.log" || fail "placeholder error is not actionable"

MOCK_BIN="$TEST_DIR/bin"
mkdir -p "$MOCK_BIN"
cat >"$MOCK_BIN/docker" <<'EOF'
#!/bin/sh
if [ -n "${DOCKER_CALL_LOG:-}" ]; then
    echo "$*" >>"$DOCKER_CALL_LOG"
fi
case "$1" in
    info)
        exit 0
        ;;
    compose)
        case "$*" in
            *" ps -q backend")
                echo backend-id
                ;;
            *" ps -q frontend")
                echo frontend-id
                ;;
        esac
        exit 0
        ;;
    network)
        exit 0
        ;;
    inspect)
        case "$*" in
            *backend-id)
                echo healthy
                ;;
            *frontend-id)
                echo running
                ;;
        esac
        exit 0
        ;;
esac
exit 1
EOF
chmod +x "$MOCK_BIN/docker"

cat >"$MOCK_BIN/git" <<'EOF'
#!/bin/sh
exit 0
EOF
chmod +x "$MOCK_BIN/git"

cat >"$MOCK_BIN/curl" <<'EOF'
#!/bin/sh
echo "$*" >>"$CURL_CALL_LOG"
exit 0
EOF
chmod +x "$MOCK_BIN/curl"

VALID_ENV="$TEST_DIR/valid.env"
cat >"$VALID_ENV" <<'EOF'
APEX_HTTP_PORT=8088
MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DATABASE=apex
MYSQL_USER=apex_app
MYSQL_PASSWORD=test-password
APEX_LOCAL_PASSWORD=test-login-password
APEX_JWT_SECRET=0123456789abcdef0123456789abcdef
EOF

PATH="$MOCK_BIN:$PATH" APEX_ENV_FILE="$VALID_ENV" \
    sh "$DEPLOY_SCRIPT" --check >"$TEST_DIR/check.log" 2>&1 \
    || fail "valid prerequisite check failed"
grep -q '检查通过' "$TEST_DIR/check.log" || fail "successful check was not reported"

CUSTOM_NETWORK_ENV="$TEST_DIR/custom-network.env"
cp "$VALID_ENV" "$CUSTOM_NETWORK_ENV"
echo 'MYSQL_DOCKER_NETWORK=apex_mysql' >>"$CUSTOM_NETWORK_ENV"
PATH="$MOCK_BIN:$PATH" APEX_ENV_FILE="$CUSTOM_NETWORK_ENV" \
    sh "$DEPLOY_SCRIPT" --check >"$TEST_DIR/custom-network.log" 2>&1 \
    || fail "custom MySQL network should be accepted"
grep -q '检查通过' "$TEST_DIR/custom-network.log" || fail "custom network check was not reported"

BACKEND_DOCKER_LOG="$TEST_DIR/backend-docker.log"
BACKEND_CURL_LOG="$TEST_DIR/backend-curl.log"
PATH="$MOCK_BIN:$PATH" APEX_ENV_FILE="$VALID_ENV" \
    DOCKER_CALL_LOG="$BACKEND_DOCKER_LOG" CURL_CALL_LOG="$BACKEND_CURL_LOG" \
    sh "$DEPLOY_SCRIPT" --be >"$TEST_DIR/backend.log" 2>&1 \
    || fail "backend-only deployment failed"
grep -q 'up -d --build --no-deps backend$' "$BACKEND_DOCKER_LOG" || fail "backend-only deployment selected wrong services"
test ! -s "$BACKEND_CURL_LOG" || fail "backend-only deployment should rely on container health"

FRONTEND_DOCKER_LOG="$TEST_DIR/frontend-docker.log"
FRONTEND_CURL_LOG="$TEST_DIR/frontend-curl.log"
PATH="$MOCK_BIN:$PATH" APEX_ENV_FILE="$VALID_ENV" \
    DOCKER_CALL_LOG="$FRONTEND_DOCKER_LOG" CURL_CALL_LOG="$FRONTEND_CURL_LOG" \
    sh "$DEPLOY_SCRIPT" --fe >"$TEST_DIR/frontend.log" 2>&1 \
    || fail "frontend-only deployment failed"
grep -q 'up -d --build --no-deps frontend$' "$FRONTEND_DOCKER_LOG" || fail "frontend-only deployment selected wrong services"
grep -q 'http://127.0.0.1:8088/$' "$FRONTEND_CURL_LOG" || fail "frontend-only deployment did not verify the homepage"

ROOT_LAYOUT="$TEST_DIR/root-layout"
mkdir -p "$ROOT_LAYOUT"
cp "$DEPLOY_SCRIPT" "$ROOT_LAYOUT/deploy-nas.sh"
cp "$SCRIPT_DIR/../docker-compose.prod.yml" "$ROOT_LAYOUT/docker-compose.prod.yml"
PATH="$MOCK_BIN:$PATH" APEX_ENV_FILE="$VALID_ENV" \
    sh "$ROOT_LAYOUT/deploy-nas.sh" --check >"$TEST_DIR/root-layout.log" 2>&1 \
    || fail "deployment script failed from the project root"
grep -q '检查通过' "$TEST_DIR/root-layout.log" || fail "project-root execution was not reported"

echo "PASS: deploy-nas.sh"
