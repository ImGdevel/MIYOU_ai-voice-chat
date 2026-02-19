#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
STATIC_DIR="${ROOT_DIR}/webflux-dialogue/src/main/resources/static"

if [[ ! -f "${FRONTEND_DIR}/package.json" ]]; then
	echo "[sync-frontend] frontend/package.json 파일을 찾을 수 없습니다." >&2
	exit 1
fi

if [[ ! -f "${FRONTEND_DIR}/package-lock.json" ]]; then
	echo "[sync-frontend] frontend/package-lock.json 파일을 찾을 수 없습니다." >&2
	exit 1
fi

echo "[sync-frontend] 프론트엔드 의존성 설치"
(
	cd "${FRONTEND_DIR}"
	npm ci --no-audit --no-fund
)

echo "[sync-frontend] 프론트엔드 프로덕션 빌드"
(
	cd "${FRONTEND_DIR}"
	npm run build
)

echo "[sync-frontend] 정적 리소스 경로 초기화: ${STATIC_DIR}"
rm -rf "${STATIC_DIR}"
mkdir -p "${STATIC_DIR}"

echo "[sync-frontend] 빌드 산출물 복사: ${FRONTEND_DIR}/dist -> ${STATIC_DIR}"
cp -R "${FRONTEND_DIR}/dist/." "${STATIC_DIR}/"

if [[ ! -f "${STATIC_DIR}/index.html" ]]; then
	echo "[sync-frontend] static/index.html 생성이 확인되지 않았습니다." >&2
	exit 1
fi

if [[ ! -d "${STATIC_DIR}/assets" ]]; then
	echo "[sync-frontend] static/assets 디렉터리가 생성되지 않았습니다." >&2
	exit 1
fi

if [[ -z "$(find "${STATIC_DIR}/assets" -mindepth 1 -print -quit)" ]]; then
	echo "[sync-frontend] static/assets 산출물이 비어 있습니다." >&2
	exit 1
fi

echo "[sync-frontend] 정적 리소스 동기화 완료"
