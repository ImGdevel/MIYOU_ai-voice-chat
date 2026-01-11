#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"

echo "[nginx] Prepare remote dir: ${REMOTE_DIR}/deploy/nginx"
ssh "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/deploy/nginx'"

echo "[nginx] Upload compose and nginx config"
scp docker-compose.app.yml "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.app.yml"
scp deploy/nginx/default.conf "${HOST_ALIAS}:${REMOTE_DIR}/deploy/nginx/default.conf"

echo "[nginx] Reload nginx container"
ssh "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && docker compose -f docker-compose.app.yml up -d --no-deps nginx"

echo "[nginx] Container status"
ssh "${HOST_ALIAS}" "docker ps --filter 'name=miyou-nginx' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[nginx] Done"
