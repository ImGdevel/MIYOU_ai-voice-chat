#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-app}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
LOKI_WRITE_URL="${LOKI_WRITE_URL:-}"
SSH_OPTS="${SSH_OPTS:-}"
ALLOY_INSTANCE="${ALLOY_INSTANCE:-miyou-app}"

if [[ -z "${LOKI_WRITE_URL}" ]]; then
  echo "[alloy] LOKI_WRITE_URL is required. (example: http://172.31.44.177:3100/loki/api/v1/push)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo "[alloy] Prepare remote dir: ${REMOTE_DIR}/monitoring/alloy"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/alloy'"

echo "[alloy] Upload compose and config"
scp ${SSH_OPTS} "${PROJECT_ROOT}/docker-compose.alloy.yml" "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.alloy.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/alloy/config.alloy" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/alloy/config.alloy"

echo "[alloy] Start alloy container"
ssh ${SSH_OPTS} "${HOST_ALIAS}" \
  "cd '${REMOTE_DIR}' && LOKI_WRITE_URL='${LOKI_WRITE_URL}' ALLOY_INSTANCE='${ALLOY_INSTANCE}' docker compose -f docker-compose.alloy.yml up -d alloy"

echo "[alloy] Service status"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "docker ps --filter 'name=miyou-alloy' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'"

echo "[alloy] Done"
