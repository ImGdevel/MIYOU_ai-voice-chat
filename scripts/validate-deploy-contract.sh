#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_ROOT}"

TARGET_SCRIPTS=(
  deploy/aws/deploy_remote_blue_green.sh
  deploy/aws/deploy_remote_compose.sh
  deploy/aws/deploy_remote_nginx.sh
  deploy/aws/remote_app_self_heal.sh
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

echo "[validate-deploy-contract] 하드코딩 compose 경로 검사"
if rg -n --fixed-strings -- '-f deploy/docker-compose.app.yml' "${TARGET_SCRIPTS[@]}"; then
  echo "[validate-deploy-contract] 실패: 하드코딩된 compose 경로가 남아 있습니다." >&2
  exit 1
fi

echo "[validate-deploy-contract] nginx 재생성 금지 검사"
if rg -n --fixed-strings -- '--force-recreate nginx' deploy/aws/deploy_remote_blue_green.sh deploy/aws/deploy_remote_nginx.sh; then
  echo "[validate-deploy-contract] 실패: nginx --force-recreate 사용이 남아 있습니다. reload 방식만 허용됩니다." >&2
  exit 1
fi

echo "[validate-deploy-contract] 공통 계약 스크립트 업로드 검사"
check_contains "deploy/aws/deploy_remote_blue_green.sh" "scp deploy/aws/remote_compose_contract.sh" "blue-green 스크립트에서 계약 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_compose.sh" "scp deploy/aws/remote_compose_contract.sh" "rolling 스크립트에서 계약 스크립트 업로드가 누락되었습니다."
check_contains "deploy/aws/deploy_remote_nginx.sh" "scp deploy/aws/remote_compose_contract.sh" "nginx 스크립트에서 계약 스크립트 업로드가 누락되었습니다."

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

echo "[validate-deploy-contract] blue-green 롤백 트리거 회귀 검사"
if awk '
  BEGIN { trap_seen=0; found=0 }
  /trap '\''rollback'\'' ERR/ { trap_seen=1; next }
  trap_seen && /exit 1/ { print NR ":" $0; found=1 }
  END { exit(found ? 0 : 1) }
' deploy/aws/deploy_remote_blue_green.sh; then
  echo "[validate-deploy-contract] 실패: rollback trap 이후 exit 1 사용이 감지되었습니다. false/return 기반 실패를 사용하세요." >&2
  exit 1
fi

echo "[validate-deploy-contract] OK"
