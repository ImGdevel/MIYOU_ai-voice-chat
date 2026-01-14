#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"

echo "[nginx] Prepare remote dir: ${REMOTE_DIR}/deploy/nginx"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx'"

echo "[nginx] Upload compose and nginx config"
scp docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"

echo "[nginx] Apply nginx config"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

cd "${remote_dir}"
if [[ ! -f ".env.deploy" ]]; then
  echo "[nginx] .env.deploy is missing at ${remote_dir}" >&2
  exit 1
fi

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
      APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml up -d --no-deps "${active_service}"
    else
      docker compose -f docker-compose.app.yml up -d --no-deps "${active_service}"
    fi
  fi
fi

if ! grep -q '\$app_upstream' deploy/nginx/default.conf; then
  echo "[nginx] default.conf must use \$app_upstream variable for zero-downtime reload safety" >&2
  exit 1
fi

sed -i -E "s/app_(blue|green):8081/${active_service}:8081/g" deploy/nginx/default.conf

docker compose -f docker-compose.app.yml up -d --no-deps --force-recreate nginx
EOF

echo "[nginx] Container status"
ssh "${HOST_ALIAS}" "docker ps --filter 'name=miyou-nginx' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[nginx] Done"
