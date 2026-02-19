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
DRAIN_SECONDS="${DRAIN_SECONDS:-45}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-30}"
POST_SWITCH_MONITOR_SECONDS="${POST_SWITCH_MONITOR_SECONDS:-30}"

echo "[blue-green] Prepare remote dir: ${REMOTE_DIR}"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx' '${REMOTE_DIR}/scripts' '${REMOTE_DIR}/logs'"

echo "[blue-green] Upload compose files and nginx config"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/deploy/docker-compose.app.yml"
scp .env.deploy.example "${HOST_ALIAS}:${REMOTE_DIR}/.env.deploy.example"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"
scp deploy/aws/remote_app_self_heal.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/remote_app_self_heal.sh"
scp deploy/aws/remote_compose_contract.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/remote_compose_contract.sh"
ssh "${HOST_ALIAS}" "chmod +x '${REMOTE_DIR}/scripts/remote_app_self_heal.sh' '${REMOTE_DIR}/scripts/remote_compose_contract.sh'"

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

echo "[blue-green] Sync env file contract"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

source "${remote_dir}/scripts/remote_compose_contract.sh"
sync_env_files "${remote_dir}"
EOF

echo "[blue-green] Ensure self-heal watchdog cron job"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"
cron_line="* * * * * bash ${remote_dir}/scripts/remote_app_self_heal.sh ${remote_dir} >> ${remote_dir}/logs/self-heal.log 2>&1"

if ! command -v crontab >/dev/null 2>&1; then
  echo "[blue-green] crontab not found, skip self-heal cron install" >&2
  exit 0
fi

tmp_cron="$(mktemp)"
crontab -l 2>/dev/null | grep -v 'remote_app_self_heal.sh' > "${tmp_cron}" || true
echo "${cron_line}" >> "${tmp_cron}"
crontab "${tmp_cron}"
rm -f "${tmp_cron}"
EOF

echo "[blue-green] Validate required secrets"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && grep -qE '^OPENAI_API_KEY=.+$' .env.deploy" || {
  echo "[blue-green] OPENAI_API_KEY is missing in ${REMOTE_DIR}/.env.deploy" >&2
  exit 1
}

echo "[blue-green] Deploy and switch traffic"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${APP_IMAGE}" "${HEALTH_CHECK_RETRIES}" "${HEALTH_CHECK_INTERVAL}" "${DRAIN_SECONDS}" "${STOP_TIMEOUT_SECONDS}" "${POST_SWITCH_MONITOR_SECONDS}" <<'EOF'
set -euo pipefail
remote_dir="$1"
app_image="$2"
health_retries="$3"
health_interval="$4"
drain_seconds="$5"
stop_timeout_seconds="$6"
post_switch_monitor_seconds="$7"

run_smoke_checks() {
  if ! curl -fsS http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
    echo "[blue-green] 앱 헬스체크 실패: /actuator/health" >&2
    return 1
  fi

  if ! curl -fsS http://127.0.0.1/ | grep -q 'id="root"'; then
    echo "[blue-green] 루트 페이지 검증 실패: React 엔트리(id=\"root\")를 찾지 못했습니다." >&2
    return 1
  fi

  dashboard_location="$(curl -fsS -o /dev/null -D - http://127.0.0.1/dashboard | tr -d '\r' | awk 'BEGIN{IGNORECASE=1} /^location:/{print $2; exit}')"
  if [[ -z "${dashboard_location}" || "${dashboard_location}" != *"/admin/monitoring/grafana/"* ]]; then
    echo "[blue-green] 대시보드 리다이렉트 검증 실패: location=${dashboard_location}" >&2
    return 1
  fi
}

cd "${remote_dir}"

# 배포 락 획득 (최대 5분 대기)
exec 200>/var/lock/miyou-deploy.lock
if ! flock -x -w 300 200; then
  echo "[blue-green] Another deployment is in progress, timeout after 5 minutes" >&2
  exit 1
fi
echo "[blue-green] Deployment lock acquired"

# 배포 중 플래그 설정 (self-heal 비활성화)
touch .deploy_in_progress

source "${remote_dir}/scripts/remote_compose_contract.sh"
sync_env_files "${remote_dir}"
compose_file="$(resolve_app_compose_file "${remote_dir}")"
verify_compose_contract "${remote_dir}" "${compose_file}"
echo "[blue-green] compose 파일: ${compose_file}"

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

service_running() {
  local color="$1"
  docker ps --format '{{.Names}}' | grep -q "^miyou-dialogue-app-${color}$"
}

current_file_upstream_color() {
  local color
  color="$(grep -E 'set \$app_upstream app_(blue|green):8081;' deploy/nginx/default.conf \
    | tail -n1 \
    | sed -E 's/.*app_(blue|green):8081.*/\1/' || true)"
  if [[ "${color}" == "blue" || "${color}" == "green" ]]; then
    printf '%s\n' "${color}"
  fi
}

current_runtime_upstream_color() {
  local color
  color="$(
    docker exec miyou-nginx nginx -T 2>/dev/null \
      | grep -E 'set \$app_upstream app_(blue|green):8081;' \
      | tail -n1 \
      | sed -E 's/.*app_(blue|green):8081.*/\1/' || true
  )"
  if [[ "${color}" == "blue" || "${color}" == "green" ]]; then
    printf '%s\n' "${color}"
  fi
}

if [[ "${active}" == "blue" ]] && ! service_running "blue" && service_running "green"; then
  echo "[blue-green] Active marker(blue) is stale, align active=green"
  active="green"
elif [[ "${active}" == "green" ]] && ! service_running "green" && service_running "blue"; then
  echo "[blue-green] Active marker(green) is stale, align active=blue"
  active="blue"
fi

current_upstream="$(current_runtime_upstream_color)"
if [[ -z "${current_upstream}" ]]; then
  current_upstream="$(current_file_upstream_color)"
fi
if [[ -n "${current_upstream}" ]] && [[ "${current_upstream}" != "${active}" ]] && service_running "${current_upstream}"; then
  echo "[blue-green] Active marker(${active}) differs from nginx upstream(${current_upstream}), align active=${current_upstream}"
  active="${current_upstream}"
fi

if [[ "${active}" == "blue" ]]; then
  candidate="green"
else
  candidate="blue"
fi
candidate_service="app_${candidate}"
active_service="app_${active}"

echo "[blue-green] Active=${active_service}, Candidate=${candidate_service}"

fail_with_rollback() {
  local message="$1"
  echo "[blue-green] ${message}" >&2
  false
}

reload_nginx_or_fail() {
  if docker ps --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
    if ! docker exec miyou-nginx nginx -t >/dev/null 2>&1; then
      fail_with_rollback "Nginx 설정 검증 실패"
    fi
    if ! docker exec miyou-nginx nginx -s reload >/dev/null 2>&1; then
      fail_with_rollback "Nginx 리로드 실패"
    fi
  else
    APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps nginx
  fi
}

reload_nginx_best_effort() {
  if docker ps --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
    docker exec miyou-nginx nginx -t >/dev/null 2>&1 || true
    docker exec miyou-nginx nginx -s reload >/dev/null 2>&1 || true
  else
    APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps nginx >/dev/null 2>&1 || true
  fi
}

rollback() {
  set +e
  echo "[blue-green] Rollback triggered"

  # active 슬롯이 내려갔을 수 있으므로 우선 재기동 시도
  APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps "${active_service}" >/dev/null 2>&1 || true

  # 트래픽을 active로 복구
  sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf
  reload_nginx_best_effort

  # candidate 비정상 상태 정리 (실패 중 재시작 루프 방지)
  APP_IMAGE="${app_image}" docker compose -f "${compose_file}" stop "${candidate_service}" >/dev/null 2>&1 || true

  # 배포 플래그 및 락 해제
  rm -f .deploy_in_progress
  flock -u 200 2>/dev/null || true

  echo "[blue-green] Rollback finished (active=${active_service})"
}

trap 'rollback' ERR

APP_IMAGE="${app_image}" docker compose -f "${compose_file}" pull "${candidate_service}"
APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d mongodb redis qdrant nginx
APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps "${candidate_service}"

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
  docker logs --tail 200 "miyou-dialogue-app-${candidate}" || true
  fail_with_rollback "Candidate health check failed: ${candidate_service}"
fi

sed -i -E "s/app_(blue|green):8081/${candidate_service}:8081/g" deploy/nginx/default.conf

reload_nginx_or_fail

post_switch_ok="false"
for attempt in $(seq 1 10); do
  if curl -fsS http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
    post_switch_ok="true"
    break
  fi
  echo "[blue-green] Post-switch health check retry ${attempt}/10"
  sleep 2
done
if [[ "${post_switch_ok}" != "true" ]]; then
  fail_with_rollback "Post-switch health check failed"
fi

run_smoke_checks

# 전환 직후 candidate 안정화 모니터링. 실패 시 ERR trap이 롤백 수행.
if [[ "${post_switch_monitor_seconds}" =~ ^[0-9]+$ ]] && [[ "${post_switch_monitor_seconds}" -gt 0 ]]; then
  echo "[blue-green] Monitor candidate health for ${post_switch_monitor_seconds}s"
  monitor_elapsed=0
  while [[ "${monitor_elapsed}" -lt "${post_switch_monitor_seconds}" ]]; do
    if ! docker run --rm --network miyou_miyou-network curlimages/curl:8.10.1 -fsS \
        "http://${candidate_service}:8081/actuator/health" | grep -q '"status":"UP"'; then
      fail_with_rollback "Candidate became unhealthy during monitor window"
    fi
    sleep 5
    monitor_elapsed=$((monitor_elapsed + 5))
  done
fi

if [[ "${drain_seconds}" =~ ^[0-9]+$ ]] && [[ "${drain_seconds}" -gt 0 ]]; then
  echo "[blue-green] Drain old app for ${drain_seconds}s: ${active_service}"
  sleep "${drain_seconds}"
fi

if [[ "${stop_timeout_seconds}" =~ ^[0-9]+$ ]] && [[ "${stop_timeout_seconds}" -gt 0 ]]; then
  docker stop -t "${stop_timeout_seconds}" "miyou-dialogue-app-${active}" >/dev/null 2>&1 || true
else
  APP_IMAGE="${app_image}" docker compose -f "${compose_file}" stop "${active_service}" || true
fi
APP_IMAGE="${app_image}" docker compose -f "${compose_file}" rm -f "${active_service}" || true
docker rm -f miyou-dialogue-app >/dev/null 2>&1 || true
echo "${candidate}" > .active_color
echo "${app_image}" > .app_image

# 배포 완료 - 플래그 제거 및 락 해제
rm -f .deploy_in_progress
trap - ERR
flock -u 200 || true

echo "[blue-green] Switched active app to ${candidate_service}"
EOF

echo "[blue-green] Container status"
ssh "${HOST_ALIAS}" "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[blue-green] Done"
