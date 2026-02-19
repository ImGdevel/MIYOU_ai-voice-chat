#!/usr/bin/env bash
set -euo pipefail

HOST_ALIAS="${1:-}"
REMOTE_DIR="${REMOTE_DIR:-/opt/app/miyou}"

run_check() {
  local remote_dir="$1"
  cd "${remote_dir}"

  marker_color=""
  if [[ -f ".active_color" ]]; then
    marker_color="$(tr -d '\r\n' < .active_color)"
  fi

  compose_marker=""
  if [[ -f ".compose_app_file" ]]; then
    compose_marker="$(tr -d '\r\n' < .compose_app_file)"
  fi

  runtime_upstream="$(
    docker exec miyou-nginx nginx -T 2>/dev/null \
      | grep -E 'set \$app_upstream app_(blue|green):8081;' \
      | tail -n1 \
      | sed -E 's/.*app_(blue|green):8081.*/\1/' || true
  )"

  blue_running="no"
  green_running="no"
  if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-blue$'; then
    blue_running="yes"
  fi
  if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-green$'; then
    green_running="yes"
  fi

  decided_color="${marker_color}"
  if [[ "${runtime_upstream}" == "blue" || "${runtime_upstream}" == "green" ]]; then
    decided_color="${runtime_upstream}"
  fi

  printf 'active_color(marker)=%s\n' "${marker_color:-<none>}"
  printf 'active_color(nginx-runtime)=%s\n' "${runtime_upstream:-<none>}"
  printf 'app_blue_running=%s\n' "${blue_running}"
  printf 'app_green_running=%s\n' "${green_running}"
  printf 'compose_marker=%s\n' "${compose_marker:-<none>}"
  printf 'decided_active=%s\n' "${decided_color:-<unknown>}"
}

if [[ -n "${HOST_ALIAS}" ]]; then
  ssh "${HOST_ALIAS}" "bash -s" -- "${REMOTE_DIR}" <<'EOF'
set -euo pipefail
remote_dir="$1"
cd "${remote_dir}"

marker_color=""
if [[ -f ".active_color" ]]; then
  marker_color="$(tr -d '\r\n' < .active_color)"
fi

compose_marker=""
if [[ -f ".compose_app_file" ]]; then
  compose_marker="$(tr -d '\r\n' < .compose_app_file)"
fi

runtime_upstream="$(
  docker exec miyou-nginx nginx -T 2>/dev/null \
    | grep -E 'set \$app_upstream app_(blue|green):8081;' \
    | tail -n1 \
    | sed -E 's/.*app_(blue|green):8081.*/\1/' || true
)"

blue_running="no"
green_running="no"
if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-blue$'; then
  blue_running="yes"
fi
if docker ps --format '{{.Names}}' | grep -q '^miyou-dialogue-app-green$'; then
  green_running="yes"
fi

decided_color="${marker_color}"
if [[ "${runtime_upstream}" == "blue" || "${runtime_upstream}" == "green" ]]; then
  decided_color="${runtime_upstream}"
fi

printf 'active_color(marker)=%s\n' "${marker_color:-<none>}"
printf 'active_color(nginx-runtime)=%s\n' "${runtime_upstream:-<none>}"
printf 'app_blue_running=%s\n' "${blue_running}"
printf 'app_green_running=%s\n' "${green_running}"
printf 'compose_marker=%s\n' "${compose_marker:-<none>}"
printf 'decided_active=%s\n' "${decided_color:-<unknown>}"
EOF
else
  run_check "${REMOTE_DIR}"
fi
