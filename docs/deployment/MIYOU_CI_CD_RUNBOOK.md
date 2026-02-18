# MIYOU CI/CD 운영 런북

본 문서는 현재 프로젝트의 **GitHub Actions + GHCR + EC2(Docker Compose) + Nginx** 배포 구조를 운영 관점에서 정리한다.
구현 상세(시퀀스/계약/확장 전략)는 `docs/spec/tech/nginx-cicd-deploy/README.md`를 함께 참고한다.

## 1. 현재 배포 아키텍처
- CI: GitHub Actions
- 이미지 저장소: GHCR (`ghcr.io/<owner>/miyou-dialogue`)
- 서버: EC2 (Ubuntu + Docker + Docker Compose)
- 설정/시크릿: AWS SSM Parameter Store (`/miyou/prod/*`)
- 진입점: Nginx(80) -> App(8081, 내부 노출)
- 앱 배포 토폴로지: `app_blue` / `app_green` 2개 슬롯 기반

## 2. 워크플로우 개요
파일: `.github/workflows/ci-cd.yml`

트리거:
- `workflow_dispatch` (수동 실행)
  - 입력값:
    - `deploy_scope`: `full | nginx_only` (기본 `full`)
    - `deploy_strategy`: `blue_green | rolling` (기본 `blue_green`)
    - `deploy_nginx`: boolean (기본 `false`)

잡 순서:
1. `detect-nginx-changes`: 직전 커밋 대비 Nginx 변경 감지
2. `gradle-build`: 테스트 + `bootJar`
   - Gradle build cache 사용(`--build-cache`)
3. `build-and-push`: Docker 이미지 빌드/푸시
   - Buildx GHA cache scope 고정(`miyou-dialogue-image`)
   - Dockerfile Gradle 캐시 마운트 적용
4. `deploy`: 서버 앱/스토리지 스택 배포
   - `blue_green`: `deploy_remote_blue_green.sh` 실행
   - `rolling`: `deploy_remote_compose.sh` 실행
5. `deploy_nginx`: 조건부 Nginx 배포
   - 조건 A: `deploy_nginx=true`
   - 조건 B: `deploy/nginx/**` 또는 `docker-compose.app.yml` 변경 감지
6. `notify`: Discord Embed 알림 전송

## 3. 배포 스크립트 역할
- `scripts/aws/deploy_remote_compose.sh`
  - compose, env, nginx conf 동기화
  - SSM에서 `.env.deploy` 생성
  - 현재 활성 색상(`.active_color`) 기준 서비스 pull/up
  - self-heal watchdog cron(1분 주기) 설치
  - 마지막 배포 이미지 `.app_image` 기록
- `scripts/aws/deploy_remote_nginx.sh`
  - nginx conf 동기화
  - 현재 활성 색상 기준 proxy 대상 반영
  - nginx 컨테이너 `--force-recreate`로 설정 반영 일관성 확보
  - active 슬롯 미실행 시 fallback 슬롯 자동 전환
  - blue/green 둘 다 미실행 시 active 슬롯 선기동 후 nginx 재생성
- `scripts/aws/deploy_remote_blue_green.sh`
  - 비활성 슬롯(blue/green) 이미지 pull/up
  - 후보 슬롯 health check(`/actuator/health`) 통과 시 Nginx 스위치
  - 스위치 후 health 재검증 실패 시 즉시 롤백
  - 전환 후 모니터링 구간(`POST_SWITCH_MONITOR_SECONDS`) 내 실패 시 롤백
  - 구 슬롯 종료 전 드레인 타임 적용 후 graceful stop
  - 성공 시 기존 활성 슬롯 중지/삭제, `.active_color` 갱신
  - self-heal watchdog cron(1분 주기) 설치
  - 마지막 배포 이미지 `.app_image` 기록
  - 조정 가능 변수:
    - `DRAIN_SECONDS` (기본 45)
    - `STOP_TIMEOUT_SECONDS` (기본 30)
    - `POST_SWITCH_MONITOR_SECONDS` (기본 120)

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
3) `deploy_scope` 선택
   - `full`: 앱 + 조건부 Nginx 배포
   - `nginx_only`: 앱 빌드/배포 생략, Nginx만 배포
4) `deploy_strategy` 선택
   - `blue_green` (권장): 무중단에 가까운 전환
   - `rolling`: 단순 롤링 방식
5) `deploy_nginx` 선택
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
- Blue/Green 전환 중 Nginx 502:
  - 원인: 후보 슬롯 기동 전 트래픽 스위치 또는 잘못된 proxy 대상
  - 대응: 후보 슬롯 health check 통과 후 스위치, 실패 시 즉시 롤백
- `host not found in upstream app_blue`:
  - 원인: `.active_color`와 실제 실행 슬롯 불일치 상태에서 nginx reload
  - 대응: nginx 배포 스크립트가 실행 중 슬롯 기준 fallback/선기동 후 reload

## 10. 커밋 정책 (Nginx 관련)
- 커밋 권장:
  - `deploy/nginx/default.conf`
  - compose/워크플로우/배포 스크립트
- 커밋 금지:
  - 인증서/개인키
  - 실제 시크릿 값(.env 실값, 토큰, API 키)

## 11. 작업 종료 문서화 규칙
- 배포/운영 로직 변경 시 반드시 본 런북 업데이트
- 변경 대상:
  - 워크플로우 입력/동작 순서
  - 배포 스크립트 동작/롤백 방식
  - 운영자가 실행하는 절차와 점검 포인트
- PR/커밋 전 최소 확인:
  - 문서와 실제 스크립트 동작 불일치 여부
  - 신규 환경변수/시크릿 요구사항 반영 여부
