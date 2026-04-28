#!/usr/bin/env bash

runtime_cleanup_log() {
  printf '[runtime-cleanup] %s\n' "$*" >&2
}

runtime_cleanup_root_usage_percent() {
  local target="${1:-/}"
  df -P "${target}" 2>/dev/null | awk 'NR == 2 { gsub("%", "", $5); print $5 }'
}

runtime_cleanup_show_disk() {
  local label="$1"

  runtime_cleanup_log "disk usage (${label})"
  df -h / 2>/dev/null || true

  if command -v docker >/dev/null 2>&1; then
    runtime_cleanup_log "docker usage (${label})"
    docker system df 2>/dev/null || true
  fi
}

run_runtime_cleanup() {
  local remote_dir="${1:-}"
  local fail_threshold="${DISK_USAGE_FAIL_THRESHOLD:-95}"
  local has_docker="true"

  if [[ "${SKIP_RUNTIME_CLEANUP:-false}" == "true" ]]; then
    runtime_cleanup_log "SKIP_RUNTIME_CLEANUP=true, skip cleanup"
    return 0
  fi

  if ! command -v docker >/dev/null 2>&1; then
    has_docker="false"
    runtime_cleanup_log "docker command is missing, skip docker cleanup"
  fi

  if [[ -n "${remote_dir}" && -d "${remote_dir}" ]]; then
    runtime_cleanup_log "remote dir: ${remote_dir}"
  fi

  runtime_cleanup_show_disk "before"

  if [[ "${has_docker}" == "true" ]]; then
    runtime_cleanup_log "prune stopped containers"
    docker container prune -f --filter "until=${DOCKER_CONTAINER_PRUNE_UNTIL:-24h}" \
      || runtime_cleanup_log "stopped container prune failed"

    runtime_cleanup_log "prune unused images"
    docker image prune -af --filter "until=${DOCKER_IMAGE_PRUNE_UNTIL:-24h}" \
      || runtime_cleanup_log "unused image prune failed"

    runtime_cleanup_log "prune build cache"
    docker builder prune -af --filter "until=${DOCKER_BUILDER_PRUNE_UNTIL:-24h}" \
      || runtime_cleanup_log "build cache prune failed"
  fi

  if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
    if command -v apt-get >/dev/null 2>&1; then
      runtime_cleanup_log "clean apt package cache"
      sudo apt-get clean || runtime_cleanup_log "apt cache cleanup failed"
    fi

    if command -v journalctl >/dev/null 2>&1; then
      runtime_cleanup_log "vacuum journal logs"
      sudo journalctl --vacuum-size="${JOURNAL_VACUUM_SIZE:-100M}" \
        || runtime_cleanup_log "journal vacuum failed"
    fi

    if command -v snap >/dev/null 2>&1; then
      runtime_cleanup_log "remove disabled snap revisions"
      snap list --all 2>/dev/null \
        | awk '/disabled/{print $1, $3}' \
        | while read -r snap_name revision; do
            if [[ -n "${snap_name}" && -n "${revision}" ]]; then
              sudo snap remove "${snap_name}" --revision="${revision}" \
                || runtime_cleanup_log "disabled snap removal failed: ${snap_name} ${revision}"
            fi
          done

      if [[ -d "/var/lib/snapd/cache" ]]; then
        runtime_cleanup_log "clean snap package cache"
        sudo find /var/lib/snapd/cache -type f -delete \
          || runtime_cleanup_log "snap cache cleanup failed"
      fi
    fi
  else
    runtime_cleanup_log "passwordless sudo is unavailable, skip host package/log cleanup"
  fi

  runtime_cleanup_show_disk "after"

  local usage
  usage="$(runtime_cleanup_root_usage_percent /)"
  if [[ "${usage}" =~ ^[0-9]+$ && "${usage}" -ge "${fail_threshold}" ]]; then
    runtime_cleanup_log "root disk usage is ${usage}% after cleanup (threshold: ${fail_threshold}%)"
    return 1
  fi

  runtime_cleanup_log "cleanup completed"
}
