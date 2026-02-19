#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"

echo "[nginx] Prepare remote dir: ${REMOTE_DIR}/deploy/nginx"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx' '${REMOTE_DIR}/scripts'"

echo "[nginx] Upload compose and nginx config"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp deploy/docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/deploy/docker-compose.app.yml"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"
scp deploy/aws/remote_compose_contract.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/remote_compose_contract.sh"
ssh "${HOST_ALIAS}" "chmod +x '${REMOTE_DIR}/scripts/remote_compose_contract.sh'"

echo "[nginx] Apply nginx config"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

cd "${remote_dir}"
if [[ ! -f ".env.deploy" ]]; then
  echo "[nginx] .env.deploy is missing at ${remote_dir}" >&2
  exit 1
fi
source "${remote_dir}/scripts/remote_compose_contract.sh"
sync_env_files "${remote_dir}"
compose_file="$(resolve_app_compose_file "${remote_dir}")"
verify_compose_contract "${remote_dir}" "${compose_file}"
echo "[nginx] compose 파일: ${compose_file}"

auth_user="$(grep -E '^NGINX_BASIC_AUTH_USER=' .env.deploy | tail -n1 | cut -d'=' -f2-)"
auth_password="$(grep -E '^NGINX_BASIC_AUTH_PASSWORD=' .env.deploy | tail -n1 | cut -d'=' -f2-)"
if [[ -z "${auth_user}" || -z "${auth_password}" ]]; then
  echo "[nginx] NGINX_BASIC_AUTH_USER / NGINX_BASIC_AUTH_PASSWORD is required in .env.deploy" >&2
  exit 1
fi

mkdir -p deploy/nginx
printf '%s:%s\n' "${auth_user}" "$(openssl passwd -apr1 "${auth_password}")" > deploy/nginx/.htpasswd
chmod 644 deploy/nginx/.htpasswd

active_service="app_blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active_service="app_green"
fi

service_running() {
  local service="$1"
  local color="${service#app_}"
  docker ps --format '{{.Names}}' | grep -q "^miyou-dialogue-app-${color}$"
}

if ! service_running "${active_service}"; then
  fallback_service="app_blue"
  if [[ "${active_service}" == "app_blue" ]]; then
    fallback_service="app_green"
  fi

  if service_running "${fallback_service}"; then
    echo "[nginx] Active marker(${active_service}) is stale, switch target to ${fallback_service}"
    active_service="${fallback_service}"
  else
    echo "[nginx] No running app slot detected, start ${active_service} before nginx reload"
    app_image=""
    if [[ -f ".app_image" ]]; then
      app_image="$(cat .app_image)"
    fi
    if [[ -n "${app_image}" ]]; then
      APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps "${active_service}"
    else
      docker compose -f "${compose_file}" up -d --no-deps "${active_service}"
    fi
  fi
fi

if ! grep -q '\$app_upstream' deploy/nginx/default.conf; then
  echo "[nginx] default.conf must use \$app_upstream variable for zero-downtime reload safety" >&2
  exit 1
fi

backup_config() {
  cp deploy/nginx/default.conf deploy/nginx/default.conf.backup
}

restore_config() {
  if [[ -f deploy/nginx/default.conf.backup ]]; then
    mv deploy/nginx/default.conf.backup deploy/nginx/default.conf
    echo "[nginx] Rolled back nginx config to previous version" >&2
  fi
}

backup_config
sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf

# nginx-only 배포에서도 compose 변경(마운트/포트)이 반영되도록 reconcile을 항상 수행한다.
docker compose -f "${compose_file}" up -d --no-deps nginx

if ! docker exec miyou-nginx nginx -t >/dev/null 2>&1; then
  echo "[nginx] Config validation failed, rolling back" >&2
  restore_config
  docker compose -f "${compose_file}" up -d --no-deps nginx
  docker exec miyou-nginx nginx -s reload >/dev/null 2>&1 || true
  exit 1
fi

if ! docker exec miyou-nginx nginx -s reload >/dev/null 2>&1; then
  echo "[nginx] Reload failed, rolling back" >&2
  restore_config
  docker compose -f "${compose_file}" up -d --no-deps nginx
  docker exec miyou-nginx nginx -s reload >/dev/null 2>&1 || true
  exit 1
fi

rm -f deploy/nginx/default.conf.backup
EOF

echo "[nginx] Container status"
ssh "${HOST_ALIAS}" "docker ps --filter 'name=miyou-nginx' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[nginx] Done"
