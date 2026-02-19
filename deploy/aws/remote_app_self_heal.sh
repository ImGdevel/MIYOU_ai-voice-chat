#!/usr/bin/env bash
set -euo pipefail

REMOTE_DIR="${1:-/opt/app/miyou}"

if [[ ! -d "${REMOTE_DIR}" ]]; then
  exit 0
fi

cd "${REMOTE_DIR}"
if [[ ! -f "${REMOTE_DIR}/scripts/remote_compose_contract.sh" ]]; then
  echo "[self-heal] compose 계약 스크립트를 찾을 수 없어 복구를 건너뜁니다: ${REMOTE_DIR}/scripts/remote_compose_contract.sh" >&2
  exit 0
fi
source "${REMOTE_DIR}/scripts/remote_compose_contract.sh"

if ! sync_env_files "${REMOTE_DIR}"; then
  echo "[self-heal] env 계약 동기화에 실패해 복구를 중단합니다." >&2
  exit 0
fi

if ! compose_file="$(resolve_app_compose_file "${REMOTE_DIR}")"; then
  echo "[self-heal] compose 경로를 찾지 못해 복구를 중단합니다." >&2
  exit 0
fi

if ! verify_compose_contract "${REMOTE_DIR}" "${compose_file}"; then
  echo "[self-heal] compose 계약 검증 실패로 복구를 중단합니다." >&2
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
  APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d mongodb redis qdrant "${active_service}" nginx
else
  docker compose -f "${compose_file}" up -d mongodb redis qdrant "${active_service}" nginx
fi
