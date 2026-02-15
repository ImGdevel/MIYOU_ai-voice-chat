#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-monitoring}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou-monitoring}"
APP_METRICS_TARGET="${APP_METRICS_TARGET:-}"
SSH_OPTS="${SSH_OPTS:-}"
USE_SSM="${USE_SSM:-true}"
SSM_WEBHOOK_PARAM="${SSM_WEBHOOK_PARAM:-/miyou/prod/WEB_HOOK_GRAFANA}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

if [[ -z "${APP_METRICS_TARGET}" ]]; then
  echo "[monitoring] APP_METRICS_TARGET is required. (example: 172.31.62.169:80)" >&2
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

echo "[monitoring] Prepare remote dir: ${REMOTE_DIR}"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/prometheus/targets'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/loki'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/grafana/provisioning/datasources'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/grafana/provisioning/dashboards'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/grafana/provisioning/alerting/rules'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "mkdir -p '${REMOTE_DIR}/monitoring/grafana/dashboards'"

echo "[monitoring] Upload compose and config"
scp ${SSH_OPTS} "${PROJECT_ROOT}/docker-compose.monitoring.yml" "${HOST_ALIAS}:${REMOTE_DIR}/docker-compose.monitoring.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/prometheus/prometheus.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/prometheus/prometheus.yml"
scp ${SSH_OPTS} "${TMP_TARGET_FILE}" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/prometheus/targets/app-targets.json"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/loki/loki-config.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/loki/loki-config.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/provisioning/datasources/datasources.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/provisioning/datasources/datasources.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/provisioning/dashboards/dashboards.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/provisioning/dashboards/dashboards.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/provisioning/alerting/alerting.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/provisioning/alerting/alerting.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/provisioning/alerting/rules/miyou-alerts.yml" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/provisioning/alerting/rules/miyou-alerts.yml"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/dashboards/miyou-overview.json" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-overview.json"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/dashboards/miyou-jvm-infra.json" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-jvm-infra.json"
scp ${SSH_OPTS} "${PROJECT_ROOT}/monitoring/grafana/dashboards/miyou-tts.json" "${HOST_ALIAS}:${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-tts.json"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "chmod 644 '${REMOTE_DIR}/monitoring/prometheus/prometheus.yml' '${REMOTE_DIR}/monitoring/prometheus/targets/app-targets.json'"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "chmod 644 '${REMOTE_DIR}/monitoring/loki/loki-config.yml' '${REMOTE_DIR}/monitoring/grafana/provisioning/datasources/datasources.yml' '${REMOTE_DIR}/monitoring/grafana/provisioning/dashboards/dashboards.yml' '${REMOTE_DIR}/monitoring/grafana/provisioning/alerting/alerting.yml' '${REMOTE_DIR}/monitoring/grafana/provisioning/alerting/rules/miyou-alerts.yml' '${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-overview.json' '${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-jvm-infra.json' '${REMOTE_DIR}/monitoring/grafana/dashboards/miyou-tts.json'"

if [[ "${USE_SSM}" == "true" ]]; then
  echo "[monitoring] Resolve Discord webhook from SSM: ${SSM_WEBHOOK_PARAM}"
  ssh ${SSH_OPTS} "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${SSM_WEBHOOK_PARAM}" "${AWS_REGION}" <<'EOF'
set -euo pipefail
remote_dir="$1"
ssm_param="$2"
aws_region="$3"

if ! command -v aws >/dev/null 2>&1; then
  echo "[monitoring] aws cli is missing on remote host" >&2
  exit 1
fi

webhook_url="$(aws ssm get-parameter \
  --name "${ssm_param}" \
  --with-decryption \
  --region "${aws_region}" \
  --query 'Parameter.Value' \
  --output text 2>/dev/null || true)"

if [[ -z "${webhook_url}" || "${webhook_url}" == "None" ]]; then
  if [[ -f "${remote_dir}/.env.monitoring" ]]; then
    echo "[monitoring] SSM read failed, keep existing .env.monitoring" >&2
    exit 0
  fi
  echo "[monitoring] SSM webhook parameter is empty or inaccessible: ${ssm_param}" >&2
  exit 1
fi

cat > "${remote_dir}/.env.monitoring" <<EOT
DISCORD_WEBHOOK_URL=${webhook_url}
EOT
chmod 600 "${remote_dir}/.env.monitoring"
EOF
else
  echo "[monitoring] USE_SSM=false, existing .env.monitoring will be used"
  ssh ${SSH_OPTS} "${HOST_ALIAS}" "test -f '${REMOTE_DIR}/.env.monitoring' || { echo '[monitoring] .env.monitoring not found'; exit 1; }"
fi

echo "[monitoring] Start services"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "cd '${REMOTE_DIR}' && docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d prometheus loki grafana"

echo "[monitoring] Service status"
ssh ${SSH_OPTS} "${HOST_ALIAS}" "docker ps --filter 'name=miyou-' --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"

echo "[monitoring] Done"
