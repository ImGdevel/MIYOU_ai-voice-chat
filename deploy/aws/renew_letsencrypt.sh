#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"
CERTBOT_IMAGE="${CERTBOT_IMAGE:-certbot/certbot:latest}"

echo "[letsencrypt] Renew SSL certificates on ${HOST_ALIAS}"

ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" "${CERTBOT_IMAGE}" <<'EOF'
set -euo pipefail
remote_dir="$1"
certbot_image="$2"

cd "${remote_dir}"

if [[ ! -d "deploy/certbot/conf" ]]; then
  echo "[letsencrypt] deploy/certbot/conf directory not found, skip renewal" >&2
  exit 0
fi

echo "[letsencrypt] Run certbot renew"
docker run --rm \
  -v "${remote_dir}/deploy/certbot/conf:/etc/letsencrypt" \
  -v "${remote_dir}/deploy/certbot/www:/var/www/certbot" \
  "${certbot_image}" renew --webroot -w /var/www/certbot

renewal_status=$?

if [[ "${renewal_status}" -ne 0 ]]; then
  echo "[letsencrypt] Certbot renewal failed with status ${renewal_status}" >&2
  exit "${renewal_status}"
fi

echo "[letsencrypt] Check if nginx is running"
if docker ps --format '{{.Names}}' | grep -q '^miyou-nginx$'; then
  echo "[letsencrypt] Reload nginx to apply new certificates"
  if docker exec miyou-nginx nginx -s reload >/dev/null 2>&1; then
    echo "[letsencrypt] Nginx reloaded successfully"
  else
    echo "[letsencrypt] Nginx reload failed, but certificates are renewed" >&2
    exit 1
  fi
else
  echo "[letsencrypt] Nginx not running, skip reload"
fi

echo "[letsencrypt] Certificate renewal completed"
EOF

echo "[letsencrypt] Done"
