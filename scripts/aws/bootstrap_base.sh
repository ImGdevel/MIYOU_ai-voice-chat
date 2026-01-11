#!/usr/bin/env bash
set -euo pipefail

# Minimal, repeatable EC2 bootstrap for Ubuntu 22.04 LTS
# - Installs Docker Engine + Compose plugin
# - Creates /opt/app for deployment artifacts
# - Optionally installs AWS SSM Agent

APP_DIR="${APP_DIR:-/opt/app}"
APP_USER="${APP_USER:-ubuntu}"
INSTALL_SSM_AGENT="${INSTALL_SSM_AGENT:-true}"
RUN_UPGRADE="${RUN_UPGRADE:-false}"

log() {
  printf '[bootstrap] %s\n' "$*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "Run as root (sudo required)."
    exit 1
  fi
}

detect_ubuntu() {
  if ! grep -qi 'ubuntu' /etc/os-release; then
    log "Unsupported OS. This script targets Ubuntu."
    exit 1
  fi
}

apt_install_if_missing() {
  local pkgs=()
  for p in "$@"; do
    if ! dpkg -s "$p" >/dev/null 2>&1; then
      pkgs+=("$p")
    fi
  done
  if [[ ${#pkgs[@]} -gt 0 ]]; then
    DEBIAN_FRONTEND=noninteractive apt-get install -y "${pkgs[@]}"
  fi
}

setup_docker_repo() {
  if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
  fi

  if [[ ! -f /etc/apt/sources.list.d/docker.list ]]; then
    local arch codename
    arch="$(dpkg --print-architecture)"
    codename="$(. /etc/os-release && echo "${VERSION_CODENAME}")"
    cat >/etc/apt/sources.list.d/docker.list <<EOL

deb [arch=${arch} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${codename} stable
EOL
  fi
}

install_docker() {
  apt_install_if_missing ca-certificates curl gnupg lsb-release
  setup_docker_repo
  apt-get update -y
  apt_install_if_missing docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable docker
  systemctl restart docker
}

install_ssm_agent() {
  if [[ "${INSTALL_SSM_AGENT}" != "true" ]]; then
    return 0
  fi

  if systemctl list-unit-files | grep -q '^amazon-ssm-agent.service'; then
    systemctl enable amazon-ssm-agent || true
    systemctl restart amazon-ssm-agent || true
    return 0
  fi

  if command -v snap >/dev/null 2>&1; then
    if ! snap list 2>/dev/null | awk '{print $1}' | grep -q '^amazon-ssm-agent$'; then
      snap install amazon-ssm-agent --classic
    fi
    systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service || true
    systemctl restart snap.amazon-ssm-agent.amazon-ssm-agent.service || true
  fi
}

ensure_user_group() {
  if id -u "${APP_USER}" >/dev/null 2>&1; then
    usermod -aG docker "${APP_USER}" || true
  fi
}

prepare_app_dir() {
  mkdir -p "${APP_DIR}"
  if id -u "${APP_USER}" >/dev/null 2>&1; then
    chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"
  fi
}

print_summary() {
  log "OS: $(lsb_release -ds 2>/dev/null || grep PRETTY_NAME /etc/os-release | cut -d= -f2-)"
  log "Docker: $(docker --version 2>/dev/null || echo not installed)"
  log "Compose: $(docker compose version 2>/dev/null || echo not installed)"
  log "App dir: ${APP_DIR}"
}

main() {
  require_root
  detect_ubuntu
  apt-get update -y
  if [[ "${RUN_UPGRADE}" == "true" ]]; then
    DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
  fi
  install_docker
  install_ssm_agent
  ensure_user_group
  prepare_app_dir
  print_summary
  log "Bootstrap completed."
}

main "$@"
