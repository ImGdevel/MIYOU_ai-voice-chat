# MIYOU CI/CD 운영 런북

본 문서는 현재 프로젝트의 **GitHub Actions + GHCR + EC2(Docker Compose) + Nginx** 배포 구조를 운영 관점에서 정리한다.

## 1. 현재 배포 아키텍처
- CI: GitHub Actions
- 이미지 저장소: GHCR (`ghcr.io/<owner>/miyou-dialogue`)
- 서버: EC2 (Ubuntu + Docker + Docker Compose)
- 설정/시크릿: AWS SSM Parameter Store (`/miyou/prod/*`)
- 진입점: Nginx(80) -> App(8081, 내부 노출)

## 2. 워크플로우 개요
파일: `.github/workflows/ci-cd.yml`

트리거:
- `workflow_dispatch` (수동 실행)
  - 입력값: `deploy_nginx` (boolean, 기본 `false`)

잡 순서:
1. `detect-nginx-changes`: 직전 커밋 대비 Nginx 변경 감지
2. `gradle-build`: 테스트 + `bootJar`
3. `build-and-push`: Docker 이미지 빌드/푸시
4. `deploy`: 서버 앱/스토리지 스택 배포
5. `deploy_nginx`: 조건부 Nginx 배포
   - 조건 A: `deploy_nginx=true`
   - 조건 B: `deploy/nginx/**` 또는 `docker-compose.app.yml` 변경 감지
6. `notify`: Discord Embed 알림 전송

## 3. 배포 스크립트 역할
- `scripts/aws/deploy_remote_compose.sh`
  - compose, env, nginx conf 동기화
  - SSM에서 `.env.deploy` 생성
  - 컨테이너 pull/up
- `scripts/aws/deploy_remote_nginx.sh`
  - nginx conf 동기화
  - `nginx -t` 후 `nginx -s reload` (컨테이너 존재 시)
  - 미존재 시 nginx 컨테이너 최초 생성

## 4. GitHub Secrets 목록
필수:
- `EC2_HOST`
- `EC2_USER`
- `EC2_SSH_PRIVATE_KEY` (Ed25519 PEM 가능)
- `GHCR_USERNAME`
- `GHCR_TOKEN`
- `SSM_PATH` (예: `/miyou/prod`)
- `AWS_REGION` (예: `ap-northeast-2`)

선택:
- `DISCORD_WEBHOOK_URL` (없으면 알림 스킵)

## 5. SSM 파라미터 규칙
경로 접두사:
- `/miyou/prod/`

예시 키:
- `OPENAI_API_KEY` (SecureString)
- `SUPERTONE_API_KEY`, `SUPERTONE_API_KEY_1~5` (SecureString)
- `MONGODB_URI`, `REDIS_HOST`, `REDIS_PORT` (String)
- `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_URL`, `QDRANT_COLLECTION` (String)
- `MONITORING_PERSISTENT_ENABLED` (String)

주의:
- SecureString 사용 시 IAM/KMS 권한 필요
- placeholder(`...`) 값이 남아 있지 않도록 점검 필요

## 6. 포트/보안그룹 운영 기준
- 외부 오픈:
  - `80` (Nginx)
  - `22` (운영 정책에 맞춰 제한, CI에서 SSH 배포 시 접근 가능해야 함)
- 내부 전용:
  - App `8081` (compose `expose`만 사용)
  - Mongo/Redis/Qdrant 포트 (외부 미노출)

## 7. Health Check 기준
- 엔드포인트: `GET /actuator/health`
- 외부 점검 URL: `http://<EC2_PUBLIC_IP>/actuator/health`
- 현재 앱 설정에서 actuator health/info 노출 활성화:
  - `webflux-dialogue/src/main/resources/application.yml`

## 8. 실행 방법
1) GitHub Actions > `CI-CD` > `Run workflow`
2) 브랜치 선택 (`develop` 권장)
3) `deploy_nginx` 선택
   - `false`: Nginx 변경 시에만 Nginx 배포
   - `true`: 변경 감지와 무관하게 Nginx 배포 강제

## 9. 트러블슈팅 이력(재발 방지)
- 수동 실행 버튼 미노출:
  - 원인: 워크플로우 YAML 파싱 오류
  - 대응: payload 생성 로직 단순화(jq)
- SSH 타임아웃:
  - 원인: SG 22 소스 제한
  - 대응: Runner 접근 가능 정책으로 조정(장기적으로 SSM/self-hosted 검토)
- `port is already allocated`:
  - 원인: 구 `webflux-*` 컨테이너가 동일 포트 점유
  - 대응: 구 스택 제거 + 스토리지 포트 외부 노출 제거
- health 404:
  - 원인: actuator 비활성
  - 대응: actuator 의존성/노출 설정 추가

## 10. 커밋 정책 (Nginx 관련)
- 커밋 권장:
  - `deploy/nginx/default.conf`
  - compose/워크플로우/배포 스크립트
- 커밋 금지:
  - 인증서/개인키
  - 실제 시크릿 값(.env 실값, 토큰, API 키)
