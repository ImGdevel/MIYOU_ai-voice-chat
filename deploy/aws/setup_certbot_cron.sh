#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"

echo "[certbot-cron] Setup automated Let's Encrypt certificate renewal on ${HOST_ALIAS}"

echo "[certbot-cron] Upload renewal script"
scp deploy/aws/renew_letsencrypt.sh "${HOST_ALIAS}:${REMOTE_DIR}/scripts/renew_letsencrypt.sh"
ssh "${HOST_ALIAS}" "chmod +x '${REMOTE_DIR}/scripts/renew_letsencrypt.sh'"

echo "[certbot-cron] Install cron job"
ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"

if ! command -v crontab >/dev/null 2>&1; then
  echo "[certbot-cron] crontab not found, skip cron install" >&2
  exit 0
fi

# 매월 1일과 15일 새벽 3시에 인증서 갱신 시도
cron_line="0 3 1,15 * * bash ${remote_dir}/scripts/renew_letsencrypt.sh >> ${remote_dir}/logs/certbot-renew.log 2>&1"

tmp_cron="$(mktemp)"
crontab -l 2>/dev/null | grep -v 'renew_letsencrypt.sh' > "${tmp_cron}" || true
echo "${cron_line}" >> "${tmp_cron}"
crontab "${tmp_cron}"
rm -f "${tmp_cron}"

echo "[certbot-cron] Cron job installed successfully"
crontab -l | grep 'renew_letsencrypt.sh'
EOF

echo "[certbot-cron] Done"
