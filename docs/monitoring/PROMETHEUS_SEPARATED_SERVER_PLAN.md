# Prometheus 분리 서버 구축 계획 (Phase 1)

## 목표
- 애플리케이션 서버와 모니터링 서버를 분리한다.
- 먼저 Prometheus만 구축해 `/actuator/prometheus` 스크랩을 안정화한다.

## 확정된 전제
- 모니터링 서버(EC2) 분리 운영
- 앱-모니터링 통신은 private IP 사용
- 모니터링 서버 private IP: `<MONITORING_SERVER_PRIVATE_IP>`
- 모니터링 서버 public IP: `<MONITORING_SERVER_PUBLIC_IP>`

## 작업 단계
1. 앱 메트릭 노출 준비
   - `micrometer-registry-prometheus` 의존성 추가
   - Actuator endpoint 노출에 `prometheus` 추가
2. 모니터링 서버 Prometheus 배포
   - `docker-compose.monitoring.yml`로 Prometheus 컨테이너 기동
   - `monitoring/prometheus/prometheus.yml` 적용
   - app target은 `app-targets.json`으로 외부 주입
   - target은 앱 서버 private IP의 `:80` 사용 (Nginx 경유)
3. 네트워크 보안 설정
   - 앱 서버 SG: `8081` 인바운드를 모니터링 서버(또는 SG)로만 제한
   - 모니터링 서버 SG: `9090`은 운영 정책에 맞게 제한(권장: 비공개/터널)
4. 검증
   - Prometheus `/targets`에서 `miyou-dialogue`이 `UP`
   - PromQL 조회 가능 여부 확인

## 실행 명령 (로컬 -> 모니터링 서버)
```bash
APP_METRICS_TARGET=<APP_PRIVATE_IP>:8081 \
./scripts/aws/deploy_remote_prometheus.sh ubuntu@<MONITORING_SERVER_PUBLIC_IP>
```

예시:
```bash
APP_METRICS_TARGET=<APP_PRIVATE_IP>:80 \
./scripts/aws/deploy_remote_prometheus.sh ubuntu@<MONITORING_SERVER_PUBLIC_IP>
```

## SSM 기반 타깃 주입 (권장)
- Prometheus app target은 SSM 파라미터로 관리:
  - 기본 경로: `/miyou/prod/APP_METRICS_TARGETS`
  - 값 예시: `172.31.62.169:80` 또는 `app1.internal:80,app2.internal:80`
- 배포 스크립트 우선순위:
  1. `APP_METRICS_TARGET` / `APP_METRICS_TARGETS`
  2. `USE_SSM_TARGETS=true`일 때 SSM에서 조회

실행 예시:
```bash
SSH_OPTS='-i /path/to/key.pem -o StrictHostKeyChecking=no' \
USE_SSM_TARGETS=true \
SSM_TARGETS_PARAM=/miyou/prod/APP_METRICS_TARGETS \
AWS_REGION=ap-northeast-2 \
bash ./scripts/aws/deploy_remote_prometheus.sh ubuntu@<MONITORING_SERVER_PUBLIC_IP>
```

## 지금 필요한 정보
- 앱 서버 private IP (스크랩 타겟)
- 앱 서버 Security Group ID
- 모니터링 서버 Security Group ID
- Nginx에서 `/actuator/prometheus`를 모니터링 서버 IP만 허용(deny all)했는지 확인

## 다음 Phase (Grafana)
- Prometheus 안정화 후 Grafana 분리 서버에 추가
- 대시보드/알림 룰/권한(SSO 또는 계정) 순차 적용
