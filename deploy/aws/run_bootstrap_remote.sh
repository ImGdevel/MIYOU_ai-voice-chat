#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-miyou-dev}"
REMOTE_PATH="/tmp/bootstrap_base.sh"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_SCRIPT="${SCRIPT_DIR}/bootstrap_base.sh"

if [[ ! -f "${LOCAL_SCRIPT}" ]]; then
  echo "Local script not found: ${LOCAL_SCRIPT}" >&2
  exit 1
fi

echo "[remote-bootstrap] Uploading script to ${HOST_ALIAS}:${REMOTE_PATH}"
scp "${LOCAL_SCRIPT}" "${HOST_ALIAS}:${REMOTE_PATH}"

echo "[remote-bootstrap] Executing bootstrap on ${HOST_ALIAS}"
ssh "${HOST_ALIAS}" "sudo bash ${REMOTE_PATH}"

echo "[remote-bootstrap] Done"
