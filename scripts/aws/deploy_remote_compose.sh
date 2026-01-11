#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
USE_SSM="${USE_SSM:-true}"
SSM_PATH="${SSM_PATH:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
APP_IMAGE="${APP_IMAGE:-ghcr.io/imgdevel/miyou-dialogue:latest}"

echo "[deploy] Prepare remote dir: ${REMOTE_DIR}"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}'"

echo "[deploy] Upload compose files"
scp docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp .env.deploy.example "${HOST_ALIAS}:${REMOTE_DIR}/.env.deploy.example"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx'"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"

if [[ "${USE_SSM}" == "true" ]]; then
  if [[ -z "${SSM_PATH}" ]]; then
    echo "[deploy] SSM_PATH is required when USE_SSM=true" >&2
    echo "[deploy] Example: SSM_PATH=/miyou/prod" >&2
    exit 1
  fi

  echo "[deploy] Render .env.deploy from SSM Parameter Store (${SSM_PATH})"
  ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${SSM_PATH}" "${AWS_REGION}" <<'EOF'
set -euo pipefail
remote_dir="$1"
ssm_path="$2"
aws_region="$3"

cd "${remote_dir}"
tmp_file=".env.deploy.tmp"

if ! command -v aws >/dev/null 2>&1; then
  echo "[deploy] aws cli is missing on remote host" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "[deploy] jq is missing on remote host" >&2
  exit 1
fi

aws ssm get-parameters-by-path \
  --with-decryption \
  --recursive \
  --path "${ssm_path}" \
  --region "${aws_region}" \
  --output json \
| jq -r --arg prefix "${ssm_path}/" '.Parameters[]
    | (.Name | sub("^"+$prefix; "")) as $n
    | "\($n)=\(.Value)"' > "${tmp_file}"

if [[ ! -s "${tmp_file}" ]]; then
  echo "[deploy] No SSM parameters found under ${ssm_path}" >&2
  rm -f "${tmp_file}"
  exit 1
fi

mv "${tmp_file}" .env.deploy
chmod 600 .env.deploy
EOF
else
  echo "[deploy] Ensure env file exists"
  ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && if [ ! -f .env.deploy ]; then cp .env.deploy.example .env.deploy; fi"
fi

echo "[deploy] Validate required secrets"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && grep -qE '^OPENAI_API_KEY=.+$' .env.deploy" || {
  echo "[deploy] OPENAI_API_KEY is missing in ${REMOTE_DIR}/.env.deploy" >&2
  echo "[deploy] Set OPENAI_API_KEY and rerun deploy." >&2
  exit 1
}

echo "[deploy] Pull and start containers"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && APP_IMAGE='${APP_IMAGE}' docker compose -f docker-compose.app.yml pull && APP_IMAGE='${APP_IMAGE}' docker compose -f docker-compose.app.yml up -d"

echo "[deploy] Container status"
ssh "${HOST_ALIAS}" "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[deploy] Done"
