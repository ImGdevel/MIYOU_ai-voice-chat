#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_ROOT}"

TARGET_SCRIPTS=(
  deploy/aws/deploy_remote_blue_green.sh
  deploy/aws/deploy_remote_compose.sh
  deploy/aws/deploy_remote_nginx.sh
  deploy/aws/remote_app_self_heal.sh
  deploy/aws/remote_runtime_cleanup.sh
  deploy/aws/verify_remote_backend.sh
)

check_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -Fq "${pattern}" "${file}"; then
    printf '[validate-deploy-contract] %s (%s)\n' "${message}" "${file}" >&2
    exit 1
  fi
}

check_regex() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -Eq "${pattern}" "${file}"; then
    printf '[validate-deploy-contract] %s (%s)\n' "${message}" "${file}" >&2
    exit 1
  fi
}

search_fixed() {
  local pattern="$1"
  shift
  grep -nF -- "${pattern}" "$@"
}

echo "[validate-deploy-contract] 하드코딩 compose 경로 검사"
if search_fixed '-f deploy/docker-compose.app.yml' "${TARGET_SCRIPTS[@]}"; then
  echo "[validate-deploy-contract] 실패: 하드코딩된 compose 경로가 남아 있습니다." >&2
  exit 1
fi

echo "[validate-deploy-contract] nginx 재생성 금지 검사"
if search_fixed '--force-recreate nginx' deploy/aws/deploy_remote_blue_green.sh deploy/aws/deploy_remote_nginx.sh; then
  echo "[validate-deploy-contract] 실패: nginx --force-recreate 사용이 남아 있습니다. reload 방식만 허용됩니다." >&2
  exit 1
fi

echo "[validate-deploy-contract] nginx 파일 바인드 마운트 금지 검사"
if search_fixed './deploy/nginx/default.conf:/etc/nginx/conf.d/default.conf' deploy/docker-compose.app.yml; then
  echo "[validate-deploy-contract] 실패: default.conf 파일 단위 바인드 마운트는 inode 교체 시 컨테이너 반영이 누락될 수 있습니다." >&2
  exit 1
fi
if search_fixed './deploy/nginx/.htpasswd:/etc/nginx/.htpasswd' deploy/docker-compose.app.yml; then
  echo "[validate-deploy-contract] 실패: .htpasswd 파일 단위 바인드 마운트는 디렉터리 마운트로 대체해야 합니다." >&2
  exit 1
fi
check_contains "deploy/docker-compose.app.yml" "./deploy/nginx:/etc/nginx/conf.d:ro" "nginx 디렉터리 마운트가 누락되었습니다."

echo "[validate-deploy-contract] nginx conf.d 잔여 설정 검사"
extra_nginx_conf="$(find deploy/nginx -maxdepth 1 -type f -name '*.conf' ! -name 'default.conf' -print -quit)"
if [[ -n "${extra_nginx_conf}" ]]; then
  echo "[validate-deploy-contract] 실패: conf.d에 기본 설정 외 파일이 존재합니다 (${extra_nginx_conf})." >&2
  exit 1
fi

echo "[validate-deploy-contract] 공통 계약 스크립트 업로드 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "scp deploy/aws/remote_compose_contract.sh" "blue-green 스크립트에서 계약 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "scp deploy/aws/remote_compose_contract.sh" "rolling 스크립트에서 계약 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "scp deploy/aws/remote_compose_contract.sh" "nginx 스크립트에서 계약 스크립트 업로드가 누락되었습니다."

echo "[validate-deploy-contract] 런타임 정리 스크립트 연결 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "scp deploy/aws/remote_runtime_cleanup.sh" "blue-green 스크립트에서 런타임 정리 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "scp deploy/aws/remote_runtime_cleanup.sh" "rolling 스크립트에서 런타임 정리 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "scp deploy/aws/remote_runtime_cleanup.sh" "nginx 스크립트에서 런타임 정리 스크립트 업로드가 누락되었습니다."
check_regex "deploy/aws/deploy_remote_blue_green.sh" '^[[:space:]]*run_runtime_cleanup[[:space:]]+"\$\{remote_dir\}"' "blue-green 스크립트에서 배포 후 런타임 정리 호출이 누락되었습니다."
check_regex "deploy/aws/deploy_remote_compose.sh" '^[[:space:]]*run_runtime_cleanup[[:space:]]+"\$\{remote_dir\}"' "rolling 스크립트에서 배포 후 런타임 정리 호출이 누락되었습니다."
check_regex "deploy/aws/deploy_remote_nginx.sh" '^[[:space:]]*run_runtime_cleanup[[:space:]]+"\$\{remote_dir\}"' "nginx 스크립트에서 배포 후 런타임 정리 호출이 누락되었습니다."
check_contains "deploy/aws/remote_runtime_cleanup.sh" "docker image prune -af" "런타임 정리 스크립트에서 미사용 이미지 정리가 누락되었습니다."
check_contains "deploy/aws/remote_runtime_cleanup.sh" "DOCKER_IMAGE_PRUNE_UNTIL" "런타임 정리 스크립트에서 이미지 정리 보존 기간 설정이 누락되었습니다."
check_contains "deploy/aws/remote_runtime_cleanup.sh" "docker builder prune -af" "런타임 정리 스크립트에서 빌더 캐시 정리가 누락되었습니다."
if search_fixed '--volumes' deploy/aws/remote_runtime_cleanup.sh; then
  echo "[validate-deploy-contract] 실패: 런타임 정리 스크립트는 Docker volume을 삭제하면 안 됩니다." >&2
  exit 1
fi

echo "[validate-deploy-contract] compose 이중 동기화 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "\${REMOTE_DIR}/docker-compose.app.yml" "blue-green 스크립트에서 루트 compose 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_blue_green.sh" "\${REMOTE_DIR}/deploy/docker-compose.app.yml" "blue-green 스크립트에서 deploy compose 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "\${REMOTE_DIR}/docker-compose.app.yml" "rolling 스크립트에서 루트 compose 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "\${REMOTE_DIR}/deploy/docker-compose.app.yml" "rolling 스크립트에서 deploy compose 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "\${REMOTE_DIR}/docker-compose.app.yml" "nginx 스크립트에서 루트 compose 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "\${REMOTE_DIR}/deploy/docker-compose.app.yml" "nginx 스크립트에서 deploy compose 업로드가 누락되었습니다."

echo "[validate-deploy-contract] 계약 함수 사용 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "resolve_app_compose_file" "blue-green 스크립트에서 compose 경로 해석 함수 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_blue_green.sh" "verify_compose_contract" "blue-green 스크립트에서 compose 계약 검증 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_blue_green.sh" "sync_env_files" "blue-green 스크립트에서 env 동기화 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "resolve_app_compose_file" "rolling 스크립트에서 compose 경로 해석 함수 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "verify_compose_contract" "rolling 스크립트에서 compose 계약 검증 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "sync_env_files" "rolling 스크립트에서 env 동기화 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "resolve_app_compose_file" "nginx 스크립트에서 compose 경로 해석 함수 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "verify_compose_contract" "nginx 스크립트에서 compose 계약 검증 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "sync_env_files" "nginx 스크립트에서 env 동기화 호출이 누락되었습니다."
check_contains "deploy/aws/remote_app_self_heal.sh" "resolve_app_compose_file" "self-heal 스크립트에서 compose 경로 해석 함수 호출이 누락되었습니다."
check_contains "deploy/aws/remote_app_self_heal.sh" "verify_compose_contract" "self-heal 스크립트에서 compose 계약 검증 호출이 누락되었습니다."
check_contains "deploy/aws/remote_app_self_heal.sh" "sync_env_files" "self-heal 스크립트에서 env 동기화 호출이 누락되었습니다."

echo "[validate-deploy-contract] blue-green 롤백 종료 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "trap 'rollback; exit 1' ERR" "blue-green 스크립트에서 ERR trap이 rollback 후 실패 종료하지 않습니다."
check_contains "deploy/aws/deploy_remote_blue_green.sh" "rollback" "blue-green 스크립트에서 명시적 rollback 호출이 누락되었습니다."
check_contains "deploy/aws/deploy_remote_blue_green.sh" "exit 1" "blue-green 스크립트에서 rollback 후 실패 종료가 누락되었습니다."

echo "[validate-deploy-contract] OK"
