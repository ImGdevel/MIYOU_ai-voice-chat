#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
USE_SSM="${USE_SSM:-true}"
SSM_PATH="${SSM_PATH:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
APP_IMAGE="${APP_IMAGE:-ghcr.io/imgdevel/miyou-dialogue:latest}"
HEALTH_CHECK_RETRIES="${HEALTH_CHECK_RETRIES:-30}"
HEALTH_CHECK_INTERVAL="${HEALTH_CHECK_INTERVAL:-3}"

echo "[blue-green] Prepare remote dir: ${REMOTE_DIR}"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx'"

echo "[blue-green] Upload compose files and nginx config"
scp docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp .env.deploy.example "${HOST_ALIAS}:${REMOTE_DIR}/.env.deploy.example"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"

if [[ "${USE_SSM}" == "true" ]]; then
  if [[ -z "${SSM_PATH}" ]]; then
    echo "[blue-green] SSM_PATH is required when USE_SSM=true" >&2
    exit 1
  fi

  echo "[blue-green] Render .env.deploy from SSM (${SSM_PATH})"
  ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${SSM_PATH}" "${AWS_REGION}" <<'EOF'
set -euo pipefail
remote_dir="$1"
ssm_path="$2"
aws_region="$3"

cd "${remote_dir}"
tmp_file=".env.deploy.tmp"
ssm_json_file=".ssm-params.json"

if ! command -v aws >/dev/null 2>&1; then
  echo "[blue-green] aws cli is missing on remote host" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "[blue-green] jq is missing on remote host" >&2
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
  echo "[blue-green] No SSM parameters found under ${ssm_path}" >&2
  rm -f "${tmp_file}" "${ssm_json_file}"
  exit 1
fi

for key in NGINX_BASIC_AUTH_USER NGINX_BASIC_AUTH_PASSWORD; do
  param_name="${ssm_path}/${key}"
  param_type="$(jq -r --arg name "${param_name}" '.Parameters[] | select(.Name == $name) | .Type' "${ssm_json_file}")"
  if [[ -z "${param_type}" || "${param_type}" == "null" ]]; then
    echo "[blue-green] Missing SSM parameter: ${param_name}" >&2
    rm -f "${tmp_file}" "${ssm_json_file}"
    exit 1
  fi
  if [[ "${param_type}" != "SecureString" ]]; then
    echo "[blue-green] ${param_name} must be SecureString (current: ${param_type})" >&2
    rm -f "${tmp_file}" "${ssm_json_file}"
    exit 1
  fi
done

mv "${tmp_file}" .env.deploy
chmod 600 .env.deploy
rm -f "${ssm_json_file}"
EOF
else
  echo "[blue-green] Ensure env file exists"
  ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && if [ ! -f .env.deploy ]; then cp .env.deploy.example .env.deploy; fi"
fi

echo "[blue-green] Validate required secrets"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && grep -qE '^OPENAI_API_KEY=.+$' .env.deploy" || {
  echo "[blue-green] OPENAI_API_KEY is missing in ${REMOTE_DIR}/.env.deploy" >&2
  exit 1
}

echo "[blue-green] Deploy and switch traffic"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${APP_IMAGE}" "${HEALTH_CHECK_RETRIES}" "${HEALTH_CHECK_INTERVAL}" <<'EOF'
set -euo pipefail
remote_dir="$1"
app_image="$2"
health_retries="$3"
health_interval="$4"

cd "${remote_dir}"

auth_user="$(grep -E '^NGINX_BASIC_AUTH_USER=' .env.deploy | tail -n1 | cut -d'=' -f2-)"
auth_password="$(grep -E '^NGINX_BASIC_AUTH_PASSWORD=' .env.deploy | tail -n1 | cut -d'=' -f2-)"
if [[ -z "${auth_user}" || -z "${auth_password}" ]]; then
  echo "[blue-green] NGINX_BASIC_AUTH_USER / NGINX_BASIC_AUTH_PASSWORD is required in .env.deploy" >&2
  exit 1
fi

mkdir -p deploy/nginx
printf '%s:%s\n' "${auth_user}" "$(openssl passwd -apr1 "${auth_password}")" > deploy/nginx/.htpasswd
chmod 644 deploy/nginx/.htpasswd

active="blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active="green"
fi
if [[ "${active}" == "blue" ]]; then
  candidate="green"
else
  candidate="blue"
fi
candidate_service="app_${candidate}"
active_service="app_${active}"

echo "[blue-green] Active=${active_service}, Candidate=${candidate_service}"

sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf

APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml pull "${candidate_service}"
APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml up -d mongodb redis qdrant nginx "${candidate_service}"

health_ok="false"
for attempt in $(seq 1 "${health_retries}"); do
  if docker run --rm --network miyou_miyou-network curlimages/curl:8.10.1 -fsS \
      "http://${candidate_service}:8081/actuator/health" | grep -q '"status":"UP"'; then
    health_ok="true"
    break
  fi
  echo "[blue-green] Health check retry ${attempt}/${health_retries}"
  sleep "${health_interval}"
done

if [[ "${health_ok}" != "true" ]]; then
  echo "[blue-green] Candidate health check failed: ${candidate_service}" >&2
  docker logs --tail 200 "miyou-dialogue-app-${candidate}" || true
  exit 1
fi

sed -i -E "s/app_(blue|green):8081/${candidate_service}:8081/g" deploy/nginx/default.conf

docker exec miyou-nginx nginx -t
docker exec miyou-nginx nginx -s reload

if ! curl -fsS http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
  echo "[blue-green] Post-switch health check failed, rollback starts" >&2
  sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf
  docker exec miyou-nginx nginx -t
  docker exec miyou-nginx nginx -s reload
  exit 1
fi

APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml stop "${active_service}" || true
APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml rm -f "${active_service}" || true
docker rm -f miyou-dialogue-app >/dev/null 2>&1 || true
echo "${candidate}" > .active_color

echo "[blue-green] Switched active app to ${candidate_service}"
EOF

echo "[blue-green] Container status"
ssh "${HOST_ALIAS}" "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[blue-green] Done"
