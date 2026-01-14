# 배포 & 인프라

## 전체 인프라 구성

```mermaid
graph TB
    subgraph AWS["AWS EC2 (Ubuntu 22.04)"]
        subgraph Monitor["모니터링 서버"]
            Prom["Prometheus :9090"]
            Grafana["Grafana :3000"]
            Loki["Loki"]
            AM["Alertmanager :9093"]
        end

        subgraph AppServer["애플리케이션 서버"]
            Nginx["Nginx :80\n(리버스 프록시 + Basic Auth)"]

            subgraph BlueGreen["Blue-Green 인스턴스"]
                Blue["Blue App :8081"]
                Green["Green App :8081"]
            end

            subgraph Data["데이터 레이어"]
                Mongo[("MongoDB :27017")]
                Redis[("Redis :6379")]
                Qdrant[("Qdrant :6333")]
            end
        end
    end

    Internet["인터넷"] --> Nginx
    Nginx -->|활성 인스턴스| Blue
    Nginx -.->|대기 인스턴스| Green
    Blue --> Mongo
    Blue --> Redis
    Blue --> Qdrant
    Green --> Mongo
    Green --> Redis
    Green --> Qdrant
    AppServer -->|메트릭 스크레이핑| Monitor
```

---

## Blue-Green 무중단 배포

```mermaid
sequenceDiagram
    participant Dev as 개발자
    participant Script as 배포 스크립트
    participant Nginx as Nginx
    participant Blue as Blue (현재 활성)
    participant Green as Green (대기)

    Dev->>Script: ./deploy.sh green
    Script->>Green: 새 이미지로 컨테이너 기동
    Script->>Green: 헬스체크 대기 (/actuator/health)
    Green-->>Script: UP
    Script->>Nginx: upstream을 green으로 전환
    Nginx-->>Blue: 트래픽 중단
    Nginx-->>Green: 트래픽 시작
    Script->>Blue: 이전 컨테이너 유지 (롤백 대기)
    Note over Blue,Green: 두 인스턴스 모두 동일 MongoDB/Redis/Qdrant 사용
```

**stale 마커 자동 보정**: 배포 실패 시 active 마커가 잘못된 인스턴스를 가리키는 문제를 자동 감지·보정합니다.

---

## 시크릿 관리 (AWS SSM Parameter Store)

```mermaid
flowchart LR
    SSM["AWS SSM\nParameter Store"] -->|"배포 시\n환경변수 주입"| ENV[".env 파일"]
    ENV --> App["Spring Boot App"]
    SSM -->|암호화 저장| KEYS["OPENAI_API_KEY\nSUPERTONE_API_KEY\nMONGO_URI\n..."]
```

API 키 등 시크릿은 코드·파일에 포함되지 않으며, 배포 시점에 SSM에서 주입됩니다.

---

## Docker 이미지 빌드 (멀티 스테이지)

```dockerfile
# 1단계: 빌드 (의존성 캐시 최적화)
FROM gradle:8-jdk21 AS builder
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon   # 의존성 레이어 캐시
COPY src ./src
RUN gradle bootJar --no-daemon

# 2단계: 실행 (JRE만 포함, 이미지 경량화)
FROM eclipse-temurin:21-jre-jammy
COPY --from=builder /app/build/libs/app.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

## 로컬 개발 환경

```bash
# 인프라만 기동 (MongoDB, Redis, Qdrant)
docker-compose up -d

# 앱 실행
./gradlew :webflux-dialogue:bootRun

# 전체 스택 (앱 포함)
docker-compose -f docker-compose.app.yml up -d

# 모니터링 스택
docker-compose -f docker-compose.monitoring.yml up -d
```

| 서비스 | 로컬 포트 |
|--------|----------|
| 애플리케이션 | 8081 |
| MongoDB | 27018 |
| Redis | 16379 |
| Qdrant | 6333 |
| Grafana | 3000 |
| Prometheus | 9090 |

---

## 관련 문서

- [AWS 배포 가이드](../deployment/AWS_DEPLOYMENT_GUIDE.md)
- [CI/CD 옵션](../deployment/CI_CD_OPTIONS.md)
- [Nginx 배포 스펙](../spec/tech/nginx-cicd-deploy/README.md)
