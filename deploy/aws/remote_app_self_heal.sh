#!/usr/bin/env bash
set -euo pipefail

REMOTE_DIR="${1:-/opt/app/miyou}"

if [[ ! -d "${REMOTE_DIR}" ]]; then
  exit 0
fi

cd "${REMOTE_DIR}"

# 배포 중에는 self-heal 비활성화
if [[ -f ".deploy_in_progress" ]]; then
  exit 0
fi

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

app_image="${APP_IMAGE:-}"
if [[ -z "${app_image}" && -f ".app_image" ]]; then
  app_image="$(cat .app_image)"
fi

active_service="app_blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active_service="app_green"
fi

service_running() {
  local service="$1"
  local color="${service#app_}"
  docker ps --format '{{.Names}}' | grep -q "^miyou-dialogue-app-${color}$"
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

set_active_marker() {
  local service="$1"
  local color="${service#app_}"
  echo "${color}" > .active_color
}

ensure_nginx_upstream() {
  local service="$1"

  if ! grep -q '\$app_upstream' deploy/nginx/default.conf; then
    echo "[self-heal] default.conf에 \$app_upstream 변수가 없어 nginx 동기화를 건너뜁니다." >&2
    return 0
  fi

  sed -i -E "s/app_(blue|green):8081/${service}:8081/g" deploy/nginx/default.conf

  if docker ps --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
    if docker exec miyou-nginx nginx -t >/dev/null 2>&1; then
      docker exec miyou-nginx nginx -s reload >/dev/null 2>&1 || true
      return 0
    fi
  fi

  if [[ -n "${app_image}" ]]; then
    APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d --no-deps nginx
  else
    docker compose -f "${compose_file}" up -d --no-deps nginx
  fi
}

blue_running="false"
green_running="false"
if service_running "app_blue"; then
  blue_running="true"
fi
if service_running "app_green"; then
  green_running="true"
fi

runtime_upstream="$(current_runtime_upstream_color)"

# marker가 stale이면 실행 중인 슬롯을 우선한다.
if [[ "${active_service}" == "app_blue" && "${blue_running}" != "true" && "${green_running}" == "true" ]]; then
  active_service="app_green"
  set_active_marker "${active_service}"
elif [[ "${active_service}" == "app_green" && "${green_running}" != "true" && "${blue_running}" == "true" ]]; then
  active_service="app_blue"
  set_active_marker "${active_service}"
fi

# runtime upstream이 유효하고 해당 슬롯이 실행 중이면 우선한다.
if [[ "${runtime_upstream}" == "blue" && "${blue_running}" == "true" ]]; then
  active_service="app_blue"
  set_active_marker "${active_service}"
elif [[ "${runtime_upstream}" == "green" && "${green_running}" == "true" ]]; then
  active_service="app_green"
  set_active_marker "${active_service}"
fi

if [[ "${blue_running}" != "true" && "${green_running}" != "true" ]]; then
  echo "[self-heal] No app slot running, start ${active_service}"
  if [[ -n "${app_image}" ]]; then
    APP_IMAGE="${app_image}" docker compose -f "${compose_file}" up -d mongodb redis qdrant "${active_service}" nginx
  else
    docker compose -f "${compose_file}" up -d mongodb redis qdrant "${active_service}" nginx
  fi
fi

ensure_nginx_upstream "${active_service}"
