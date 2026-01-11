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

if docker ps -a --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
  docker exec miyou-nginx nginx -t
  docker exec miyou-nginx nginx -s reload
else
  cd "${remote_dir}"
  docker compose -f docker-compose.app.yml up -d --no-deps nginx
fi
EOF

echo "[nginx] Container status"
ssh "${HOST_ALIAS}" "docker ps --filter 'name=miyou-nginx' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[nginx] Done"
