#!/usr/bin/env bash

compose_contract_log() {
  printf '[compose-contract] %s\n' "$*" >&2
}

compose_contract_error() {
  printf '[compose-contract] %s\n' "$*" >&2
}

normalize_compose_file() {
  local compose_file="$1"
  case "${compose_file}" in
    docker-compose.app.yml | deploy/docker-compose.app.yml)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

write_compose_marker() {
  local remote_dir="$1"
  local compose_file="$2"
  printf '%s\n' "${compose_file}" > "${remote_dir}/.compose_app_file"
  chmod 600 "${remote_dir}/.compose_app_file" 2>/dev/null || true
}

detect_compose_file_from_labels() {
  local remote_dir="$1"
  local service
  local config_files
  local file
  local services=(
    miyou-dialogue-app-blue
    miyou-dialogue-app-green
    miyou-mongodb
    miyou-redis
    miyou-qdrant
    miyou-nginx
  )

  for service in "${services[@]}"; do
    config_files="$(docker inspect "${service}" --format '{{ index .Config.Labels "com.docker.compose.project.config_files" }}' 2>/dev/null || true)"
    if [[ -z "${config_files}" ]]; then
      continue
    fi

    IFS=',' read -r -a files <<< "${config_files}"
    for file in "${files[@]}"; do
      file="$(printf '%s' "${file}" | xargs)"
      case "${file}" in
        "${remote_dir}/docker-compose.app.yml")
          printf 'docker-compose.app.yml\n'
          return 0
          ;;
        "${remote_dir}/deploy/docker-compose.app.yml")
          printf 'deploy/docker-compose.app.yml\n'
          return 0
          ;;
      esac
    done
  done

  return 1
}

resolve_app_compose_file() {
  local remote_dir="$1"
  local marker_file="${remote_dir}/.compose_app_file"
  local compose_file=""

  if [[ -f "${marker_file}" ]]; then
    compose_file="$(tr -d '\r\n' < "${marker_file}")"
    if normalize_compose_file "${compose_file}" && [[ -f "${remote_dir}/${compose_file}" ]]; then
      printf '%s\n' "${compose_file}"
      return 0
    fi
    compose_contract_log "저장된 compose 경로가 유효하지 않아 자동 감지를 시도합니다: ${compose_file:-<empty>}"
  fi

  if compose_file="$(detect_compose_file_from_labels "${remote_dir}")"; then
    if [[ -f "${remote_dir}/${compose_file}" ]]; then
      write_compose_marker "${remote_dir}" "${compose_file}"
      compose_contract_log "기존 컨테이너 라벨에서 compose 경로를 감지했습니다: ${compose_file}"
      printf '%s\n' "${compose_file}"
      return 0
    fi
  fi

  if [[ -f "${remote_dir}/docker-compose.app.yml" ]]; then
    compose_file="docker-compose.app.yml"
  elif [[ -f "${remote_dir}/deploy/docker-compose.app.yml" ]]; then
    compose_file="deploy/docker-compose.app.yml"
  else
    compose_contract_error "compose 파일을 찾을 수 없습니다. (${remote_dir}/docker-compose.app.yml 또는 ${remote_dir}/deploy/docker-compose.app.yml)"
    return 1
  fi

  write_compose_marker "${remote_dir}" "${compose_file}"
  compose_contract_log "기본 compose 경로를 사용합니다: ${compose_file}"
  printf '%s\n' "${compose_file}"
}

verify_compose_contract() {
  local remote_dir="$1"
  local compose_file="$2"
  local root_compose="${remote_dir}/docker-compose.app.yml"
  local deploy_compose="${remote_dir}/deploy/docker-compose.app.yml"

  if ! normalize_compose_file "${compose_file}"; then
    compose_contract_error "허용되지 않는 compose 경로입니다: ${compose_file}"
    return 1
  fi

  if [[ ! -f "${remote_dir}/${compose_file}" ]]; then
    compose_contract_error "선택된 compose 파일이 없습니다: ${remote_dir}/${compose_file}"
    return 1
  fi

  if [[ ! -f "${root_compose}" ]]; then
    compose_contract_error "표준 compose 파일이 없습니다: ${root_compose}"
    return 1
  fi

  if [[ ! -f "${deploy_compose}" ]]; then
    compose_contract_error "호환 compose 파일이 없습니다: ${deploy_compose}"
    return 1
  fi
}

sync_env_files() {
  local remote_dir="$1"
  local source_env="${remote_dir}/.env.deploy"
  local legacy_env="${remote_dir}/deploy/.env.deploy"

  if [[ ! -f "${source_env}" ]]; then
    compose_contract_error "기준 env 파일이 없습니다: ${source_env}"
    return 1
  fi

  mkdir -p "${remote_dir}/deploy"
  cp -f "${source_env}" "${legacy_env}"
  chmod 600 "${source_env}" "${legacy_env}" 2>/dev/null || true
  compose_contract_log "env 파일 동기화를 완료했습니다: .env.deploy -> deploy/.env.deploy"
}
