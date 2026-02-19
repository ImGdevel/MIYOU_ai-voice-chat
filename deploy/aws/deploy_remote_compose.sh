#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
USE_SSM="${USE_SSM:-true}"
SSM_PATH="${SSM_PATH:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
APP_IMAGE="${APP_IMAGE:-ghcr.io/imgdevel/miyou-dialogue:latest}"

echo "[deploy] Prepare remote dir: ${REMOTE_DIR}"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx' '${REMOTE_DIR}/scripts' '${REMOTE_DIR}/logs'"

echo "[deploy] Upload compose files"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/deploy/docker-compose.app.yml"
scp .env.deploy.example "${HOST_ALIAS}:${REMOTE_DIR}/.env.deploy.example"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"
scp deploy/aws/remote_app_self_heal.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/remote_app_self_heal.sh"
scp deploy/aws/remote_compose_contract.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/remote_compose_contract.sh"
ssh "${HOST_ALIAS}" "chmod +x '${REMOTE_DIR}/scripts/remote_app_self_heal.sh' '${REMOTE_DIR}/scripts/remote_compose_contract.sh'"

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
ssm_json_file=".ssm-params.json"

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
  --output json > "${ssm_json_file}"

jq -r --arg prefix "${ssm_path}/" '.Parameters[]
    | (.Name | sub("^"+$prefix; "")) as $n
    | "\($n)=\(.Value)"' "${ssm_json_file}" > "${tmp_file}"

if [[ ! -s "${tmp_file}" ]]; then
  echo "[deploy] No SSM parameters found under ${ssm_path}" >&2
  rm -f "${tmp_file}" "${ssm_json_file}"
  exit 1
fi

# NGINX 기본 인증 파라미터는 SecureString 저장을 강제한다.
for key in NGINX_BASIC_AUTH_USER NGINX_BASIC_AUTH_PASSWORD; do
  param_name="${ssm_path}/${key}"
  param_type="$(jq -r --arg name "${param_name}" '.Parameters[] | select(.Name == $name) | .Type' "${ssm_json_file}")"
  if [[ -z "${param_type}" || "${param_type}" == "null" ]]; then
    echo "[deploy] Missing SSM parameter: ${param_name}" >&2
    rm -f "${tmp_file}" "${ssm_json_file}"
    exit 1
  fi
  if [[ "${param_type}" != "SecureString" ]]; then
    echo "[deploy] ${param_name} must be SecureString (current: ${param_type})" >&2
    rm -f "${tmp_file}" "${ssm_json_file}"
    exit 1
  fi
done

mv "${tmp_file}" .env.deploy
chmod 600 .env.deploy
rm -f "${ssm_json_file}"
EOF
else
  echo "[deploy] Ensure env file exists"
  ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && if [ ! -f .env.deploy ]; then cp .env.deploy.example .env.deploy; fi"
fi

echo "[deploy] Sync env file contract"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

source "${remote_dir}/scripts/remote_compose_contract.sh"
sync_env_files "${remote_dir}"
EOF

echo "[deploy] Ensure self-heal watchdog cron job"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"
cron_line="* * * * * bash ${remote_dir}/scripts/remote_app_self_heal.sh ${remote_dir} >> ${remote_dir}/logs/self-heal.log 2>&1"

if ! command -v crontab >/dev/null 2>&1; then
  echo "[deploy] crontab not found, skip self-heal cron install" >&2
  exit 0
fi

tmp_cron="$(mktemp)"
crontab -l 2>/dev/null | grep -v 'remote_app_self_heal.sh' > "${tmp_cron}" || true
echo "${cron_line}" >> "${tmp_cron}"
crontab "${tmp_cron}"
rm -f "${tmp_cron}"
EOF

echo "[deploy] Validate required secrets"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && grep -qE '^OPENAI_API_KEY=.+$' .env.deploy" || {
  echo "[deploy] OPENAI_API_KEY is missing in ${REMOTE_DIR}/.env.deploy" >&2
  echo "[deploy] Set OPENAI_API_KEY and rerun deploy." >&2
  exit 1
}

echo "[deploy] Generate nginx basic auth file from .env.deploy"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

cd "${remote_dir}"
if [[ ! -f ".env.deploy" ]]; then
  echo "[deploy] .env.deploy is missing" >&2
  exit 1
fi

auth_user="$(grep -E '^NGINX_BASIC_AUTH_USER=' .env.deploy | tail -n1 | cut -d'=' -f2-)"
auth_password="$(grep -E '^NGINX_BASIC_AUTH_PASSWORD=' .env.deploy | tail -n1 | cut -d'=' -f2-)"

if [[ -z "${auth_user}" || -z "${auth_password}" ]]; then
  echo "[deploy] NGINX_BASIC_AUTH_USER / NGINX_BASIC_AUTH_PASSWORD is required in .env.deploy" >&2
  exit 1
fi

mkdir -p deploy/nginx
printf '%s:%s\n' "${auth_user}" "$(openssl passwd -apr1 "${auth_password}")" > deploy/nginx/.htpasswd
chmod 644 deploy/nginx/.htpasswd
EOF

echo "[deploy] Pull and start containers"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${APP_IMAGE}" <<'EOF'
set -euo pipefail
remote_dir="$1"
app_image="$2"

run_smoke_checks() {
  if ! curl -fsS http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
    echo "[deploy] 앱 헬스체크 실패: /actuator/health" >&2
    return 1
  fi

  if ! curl -fsS http://127.0.0.1/ | grep -q 'id="root"'; then
    echo "[deploy] 루트 페이지 검증 실패: React 엔트리(id=\"root\")를 찾지 못했습니다." >&2
    return 1
  fi

  dashboard_location="$(curl -fsS -o /dev/null -D - http://127.0.0.1/dashboard | tr -d '\r' | awk 'BEGIN{IGNORECASE=1} /^location:/{print $2; exit}')"
  if [[ -z "${dashboard_location}" || "${dashboard_location}" != *"/admin/monitoring/grafana/"* ]]; then
    echo "[deploy] 대시보드 리다이렉트 검증 실패: location=${dashboard_location}" >&2
    return 1
  fi
}

cd "${remote_dir}"
source "${remote_dir}/scripts/remote_compose_contract.sh"
sync_env_files "${remote_dir}"
compose_file="$(resolve_app_compose_file "${remote_dir}")"
verify_compose_contract "${remote_dir}" "${compose_file}"
echo "[deploy] compose 파일: ${compose_file}"

active_service="app_blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active_service="app_green"
fi

sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf

APP_IMAGE="${app_image}" docker compose -f "${compose_file}" pull "${active_service}" nginx mongodb redis qdrant
APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d mongodb redis qdrant nginx "${active_service}"

run_smoke_checks

echo "${app_image}" > .app_image
EOF

echo "[deploy] Container status"
ssh "${HOST_ALIAS}" "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[deploy] Done"
