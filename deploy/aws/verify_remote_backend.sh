#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
EXPECTED_APP_IMAGE="${APP_IMAGE:-}"

if [[ -z "${EXPECTED_APP_IMAGE}" ]]; then
  echo "[verify-backend] APP_IMAGE is required" >&2
  exit 1
fi

ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${EXPECTED_APP_IMAGE}" <<'EOF'
set -euo pipefail
remote_dir="$1"
expected_app_image="$2"

cd "${remote_dir}"

if [[ ! -f ".app_image" ]]; then
  echo "[verify-backend] .app_image is missing" >&2
  exit 1
fi

recorded_image="$(tr -d '\r\n' < .app_image)"
if [[ "${recorded_image}" != "${expected_app_image}" ]]; then
  echo "[verify-backend] .app_image mismatch" >&2
  echo "[verify-backend] expected=${expected_app_image}" >&2
  echo "[verify-backend] actual=${recorded_image}" >&2
  exit 1
fi

active="blue"
if [[ -f ".active_color" ]] && grep -q '^green$' .active_color; then
  active="green"
fi

container_name="miyou-dialogue-app-${active}"
if ! docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
  echo "[verify-backend] active container is not running: ${container_name}" >&2
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
  exit 1
fi

running_image="$(docker inspect "${container_name}" --format '{{.Config.Image}}')"
if [[ "${running_image}" != "${expected_app_image}" ]]; then
  echo "[verify-backend] active container image mismatch" >&2
  echo "[verify-backend] expected=${expected_app_image}" >&2
  echo "[verify-backend] actual=${running_image}" >&2
  exit 1
fi

if ! curl -fsS http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
  echo "[verify-backend] /actuator/health is not UP" >&2
  exit 1
fi

printf '[verify-backend] deployed_image=%s\n' "${running_image}"
printf '[verify-backend] active_slot=%s\n' "${active}"
printf '[verify-backend] container=%s\n' "${container_name}"
EOF
