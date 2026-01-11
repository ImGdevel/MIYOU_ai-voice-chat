#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-monitoring}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou-monitoring}"
APP_METRICS_TARGET="${APP_METRICS_TARGET:-}"
SSH_OPTS="${SSH_OPTS:-}"

if [[ -z "${APP_METRICS_TARGET}" ]]; then
  echo "[prometheus] APP_METRICS_TARGET is required. (example: 172.31.62.169:8081)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TMP_TARGET_FILE="$(mktemp)"
trap 'rm -f "${TMP_TARGET_FILE}"' EXIT

cat > "${TMP_TARGET_FILE}" <<EOF
[
  {
    "labels": {
      "service": "miyou-app",
      "env": "prod"
    },
    "targets": [
      "${APP_METRICS_TARGET}"
    ]
  }
]
EOF

echo "[prometheus] Prepare remote dir: ${REMOTE_DIR}"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/prometheus/targets'"

echo "[prometheus] Upload compose and config"
scp ${SSH_OPTS} "${PROJECT_ROOT}/docker-compose.monitoring.yml" "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.monitoring.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/prometheus/prometheus.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/prometheus/prometheus.yml"
scp ${SSH_OPTS} "${TMP_TARGET_FILE}" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/prometheus/targets/app-targets.json"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "chmod 644 '${REMOTE_DIR}/monitoring/prometheus/prometheus.yml' '${REMOTE_DIR}/monitoring/prometheus/targets/app-targets.json'"

echo "[prometheus] Start service"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && docker compose -f docker-compose.monitoring.yml up -d prometheus"

echo "[prometheus] Service status"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "docker ps --filter 'name=miyou-prometheus' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[prometheus] Done"
