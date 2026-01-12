#!/usr/bin/env bash
set -euo pipefail

REMOTE_DIR="${1:-/opt/app/miyou}"

if [[ ! -d "${REMOTE_DIR}" ]]; then
  exit 0
fi

cd "${REMOTE_DIR}"

if [[ ! -f "docker-compose.app.yml" ]]; then
  exit 0
fi

active_service="app_blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active_service="app_green"
fi

blue_running="false"
green_running="false"
if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-blue$'; then
  blue_running="true"
fi
if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-green$'; then
  green_running="true"
fi

if [[ "${blue_running}" == "true" || "${green_running}" == "true" ]]; then
  exit 0
fi

echo "[self-heal] No app slot running, start ${active_service}"

app_image="${APP_IMAGE:-}"
if [[ -z "${app_image}" && -f ".app_image" ]]; then
  app_image="$(cat .app_image)"
fi

if docker ps -a --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
  docker exec miyou-nginx nginx -t >/dev/null 2>&1 || true
fi

if [[ -n "${app_image}" ]]; then
  APP_IMAGE="${app_image}" docker compose -f docker-compose.app.yml up -d mongodb redis qdrant "${active_service}" nginx
else
  docker compose -f docker-compose.app.yml up -d mongodb redis qdrant "${active_service}" nginx
fi
