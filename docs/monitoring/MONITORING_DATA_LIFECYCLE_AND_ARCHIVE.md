# Monitoring Data Lifecycle & S3 Archive Plan

## 1) 목적
현재 운영 중인 모니터링 데이터(메트릭, 로그, 파이프라인 분석 데이터)의 저장/조회/보관 방식을 명확히 문서화하고,
장기 누적 시 발생하는 용량/성능/비용 리스크를 줄이기 위해 S3 기반 아카이브 및 복원 전략을 정의한다.

---

## 2) 현재 구조 (As-Is)

### 2.1 저장소별 역할

1. Prometheus (시계열 메트릭)
- 저장 데이터: `http_server_requests_*`, `dialogue_pipeline_*`, `llm_*`, `tts_*` 등 Prometheus 시계열
- 수집 방식: 앱 `/actuator/prometheus`를 scrape
- 보관 정책: `15d`

2. Loki (로그)
- 저장 데이터: Docker 컨테이너 로그 (Alloy가 수집)
- 수집 방식: `loki.source.docker` -> Loki push
- 보관 정책: 현재 명시적 retention 없음 (계속 누적)

3. MongoDB (애플리케이션 영속 모니터링 데이터)
- 저장 데이터:
  - `performance_metrics`
  - `usage_analytics`
  - `metrics_rollups`
  - `stage_performance_rollups`
- 보관 정책: 현재 TTL 없음 (계속 누적)

### 2.2 조회 경로

1. Grafana
- Prometheus datasource: 인프라/앱 메트릭 대시보드
- Loki datasource: 로그 검색/패널

2. 앱 자체 Dashboard (`/dashboard.html`)
- `/metrics/*` REST API를 통해 Mongo 저장 데이터를 조회

### 2.3 근거 설정/코드

- Prometheus 15일 보관
  - `docker-compose.monitoring.yml`
  - `--storage.tsdb.retention.time=15d`

- Scrape 주기
  - `monitoring/prometheus/prometheus.yml`
  - `scrape_interval: 15s`

- Loki filesystem 저장
  - `monitoring/loki/loki-config.yml`
  - `storage_config.filesystem.directory: /loki/chunks`

- Alloy 로그 수집
  - `monitoring/alloy/config.alloy`

- Mongo 영속 리포팅 활성화
  - `monitoring.persistent.enabled=true` (기본)
  - `webflux-dialogue/src/main/resources/application.yml`

- 파이프라인 저장
  - `PersistentPipelineMetricsReporter`

- 분 단위 롤업
  - `MetricsRollupScheduler` (`@Scheduled(cron = "0 * * * * *")`)

---

## 3) 현재 저장되는 데이터 상세

### 3.1 `usage_analytics`
- 요청 정보: 입력 텍스트, 길이, preview
- LLM 사용량: 모델, prompt/completion/total 토큰, 생성 문장, 완료 시간
- Retrieval: memory/document count, retrieval time
- TTS: sentence count, audio chunks, synthesis time, audio length
- 응답 지표: total/first/last latency
- 비용: llmCredits, ttsCredits, totalCredits

### 3.2 `performance_metrics`
- 파이프라인 실행 상태/시작/종료/총 지연
- first/last response latency
- stage별 status, duration, attributes

### 3.3 롤업 데이터
- `metrics_rollups`: 분 단위 요청수/토큰/총지연/평균지연
- `stage_performance_rollups`: 분 단위 stage별 count/총지연/평균지연

---

## 4) 누적 시 리스크

1. Loki 무기한 누적
- 디스크 증가, 검색 성능 저하, 운영 비용 증가

2. Mongo 모니터링 컬렉션 무기한 누적
- 인덱스/쿼리 성능 저하
- 백업/복구 시간 증가

3. Prometheus는 15일 이후 데이터 손실
- 장기 추세(월/분기) 분석이 어려움

---

## 5) 목표 상태 (To-Be)

1. Hot 데이터(빠른 조회): 로컬 스토리지
- Prometheus: 최근 15일
- Loki: 최근 7~30일
- Mongo: 최근 30~90일

2. Cold 데이터(장기 보관): S3
- 메트릭/로그/모니터링 영속 데이터 장기 저장
- 필요 시 S3에서 복원 또는 재조회

3. 복원/재조회 경로
- 운영 장애 대응 시 특정 기간 데이터 복원
- 감사/분석 요청 시 과거 데이터 리플레이 가능

---

## 6) S3 아카이브 설계안

### 6.1 1단계 (빠른 적용: 백업 + 복원 런북)

1. Mongo 백업 -> S3
- 일 배치로 `mongodump` 실행 후 S3 업로드
- 경로 예시: `s3://miyou-monitoring-archive/mongo/YYYY/MM/DD/...`

2. Prometheus 스냅샷 -> S3
- Prometheus snapshot API 호출 후 tar 업로드
- 경로 예시: `s3://miyou-monitoring-archive/prometheus/YYYY/MM/DD/...`

3. Loki 파일 백업 -> S3
- `/loki/chunks`, `/loki/index` 주기적 sync
- 경로 예시: `s3://miyou-monitoring-archive/loki/YYYY/MM/DD/...`

4. 복원 런북 문서화
- 기간 선택 -> S3 다운로드 -> 임시 환경 복원 -> 검증

장점:
- 구현 난이도 낮음
- 기존 아키텍처 변경 최소

제약:
- 즉시 쿼리형 장기조회는 어려움 (복원 후 조회)

### 6.2 2단계 (권장: 네이티브 장기 저장)

1. 메트릭 장기 보관
- 옵션 A: Thanos + S3 (Prometheus sidecar)
- 옵션 B: Mimir remote_write

2. 로그 장기 보관
- Loki `object_store: s3` 전환
- retention + compactor 정책 활성화

3. Mongo 모니터링 데이터
- TTL 인덱스 적용 (예: 90일)
- TTL 만료 전 일별 export(JSON/Parquet) -> S3

장점:
- 복원 없이 장기 조회 가능
- 운영 자동화 수준 향상

---

## 7) 복원 시나리오 (요구사항 반영)

### 7.1 시나리오 A: 특정 기간 로그 재조회 필요
1. S3에서 Loki 백업 다운로드
2. 임시 Loki 인스턴스에 데이터 마운트
3. Grafana datasource를 임시 Loki로 연결
4. 조사 완료 후 종료

### 7.2 시나리오 B: 과거 메트릭 추세 분석 필요
1. S3에서 Prometheus snapshot 다운로드
2. 임시 Prometheus로 기동
3. Grafana에서 임시 Prometheus datasource로 조회

### 7.3 시나리오 C: Mongo 기반 파이프라인 상세 복원
1. S3의 `mongodump` 아카이브 다운로드
2. 임시 Mongo 복원
3. `/metrics/*` API를 임시 환경에서 조회

---

## 8) 권장 보관 정책 (초안)

1. Prometheus
- Hot: 15일 유지 (현행)
- Cold: 일 단위 snapshot S3 보관 180일~1년

2. Loki
- Hot: 14~30일
- Cold: S3 장기 보관 180일~1년

3. Mongo 모니터링 컬렉션
- Hot: 90일 TTL
- Cold: 일 단위 export S3 1년+

---

## 9) 구현 백로그 (Action Items)

1. `ops`: S3 버킷/접근정책 생성
- KMS 암호화, 버전닝, lifecycle 적용

2. `ops`: 백업 배치 스크립트 추가
- `scripts/aws/backup_monitoring_to_s3.sh`
- Mongo/Prometheus/Loki 아카이브 업로드

3. `app`: Mongo TTL 인덱스 적용
- `usage_analytics`, `performance_metrics`, `metrics_rollups`, `stage_performance_rollups`

4. `monitoring`: Loki retention/compactor 정책 반영

5. `docs`: 복원 런북 작성
- 장애 복구, 조사용 임시 조회 환경 생성 절차

---

## 10) 운영 체크리스트

1. 백업 성공률/실패 알림 구성
2. S3 저장 용량/비용 대시보드 구성
3. 복원 리허설 월 1회 수행
4. 보관 기간/법적 요구사항 정기 검토

