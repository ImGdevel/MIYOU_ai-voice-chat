# MIYOU 모니터링 시스템 확장 계획

## 📋 목차

1. [개요](#개요)
2. [사용자 요구사항](#사용자-요구사항)
3. [구현 단계](#구현-단계)
4. [신규 메트릭](#신규-메트릭)
5. [대시보드 구성](#대시보드-구성)
6. [파일 변경 목록](#파일-변경-목록)
7. [검증 방법](#검증-방법)
8. [참고 자료](#참고-자료)

---

## 개요

MIYOU 시스템의 모니터링을 확장하여 다음 영역의 완전한 Observability를 달성합니다:

- **파이프라인 병목 분석**: Stage 간 Gap, Backpressure, 메모리 사용량
- **RAG 품질 모니터링**: Vector 검색 품질, 메모리 관련성, 검색/추출 내용
- **비용 추적**: LLM/TTS 일일/월별 비용
- **사용자 경험**: 응답 시간, 에러율
- **시스템 안정성**: Circuit Breaker, Retry, Timeout

### 최종 구성

- **대시보드**: 9개 (기존 3개 + 신규 6개)
- **총 패널**: 103개
- **신규 메트릭 카테고리**: 7개

---

## 사용자 요구사항

### ✅ 파이프라인 병목 분석

**요구사항**: "데이터가 파이프라인을 통과하는 속도를 알고 싶다"

**해결 방안**:
- Stage 간 Gap 측정 (`pipeline.stage.gap.duration`)
- Backpressure 지표 (TTS Queue size, Sentence buffer)
- Stage별 데이터 크기 추적

**대시보드**: [MIYOU Pipeline 병목 분석](#대시보드-4-miyou-pipeline-병목-분석)

### ✅ RAG 품질 모니터링

**요구사항**: "RAG가 어떤 결과를 내놓았는지, 메모리가 어떤 답변을 만들었는지"

**해결 방안**:
- Vector 검색 유사도 점수 (`rag.memory.similarity.score`)
- 메모리 중요도 분포 (`rag.memory.importance`)
- MongoDB 연동 Table로 실제 검색된 메모리 내용 표시
- 추출된 메모리 내용 Table 표시

**대시보드**: [MIYOU RAG 품질 모니터링](#대시보드-5-miyou-rag-품질-모니터링)

### ✅ 메모리 추출 품질

**요구사항**: "이번 대화에서 어떤 메모리가 추출되었나"

**해결 방안**:
- 메모리 추출 트리거 빈도 (`memory.extraction.triggered`)
- 추출 성공률 (`memory.extraction.success`)
- MongoDB Table로 추출된 메모리 내용 표시

**대시보드**: [MIYOU RAG 품질 모니터링](#대시보드-5-miyou-rag-품질-모니터링) Row 6-7

### 📈 추가 운영 효율성 메트릭

**비용 추적**:
- LLM 비용 (`llm.cost.usd`)
- TTS 비용 (`tts.cost.usd`)
- 일일/월별 비용 집계

**사용자 경험**:
- 응답 시간 분포 (`ux.response.latency`)
- 에러율 (`ux.error.rate`)

**시스템 안정성**:
- Circuit Breaker 상태 (`circuit.breaker.state`)
- Retry 성공률 (`retry.success`)
- Timeout 발생률 (`timeout.occurred`)

---

## 구현 단계

### Phase 1A: 파이프라인 병목 분석 (1-2일) - CRITICAL

#### 1.1 Stage Gap 메트릭

**목표**: Stage 간 전환 시간 측정으로 병목 지점 탐지

**파일 변경**:
- ✏️ [DialoguePipelineTracker.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/pipeline/DialoguePipelineTracker.java)
  - Stage 타임라인에서 Gap 계산 로직 추가
  - `pipeline.stage.gap.duration` Timer 등록

- ✨ [PipelineMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java) (신규)
  - Gap 메트릭 Micrometer 등록
  - Tags: `from_stage`, `to_stage`

**메트릭 예시**:
```promql
# Stage 전환 평균 Gap
avg(pipeline_stage_gap_duration_seconds) by (from_stage, to_stage)

# Top 3 병목 Gap
topk(3, pipeline_stage_gap_duration_seconds{quantile="0.95"})
```

#### 1.2 Backpressure 메트릭

**목표**: Queue 크기 및 대기 시간 추적으로 백프레셔 탐지

**파일 변경**:
- ✏️ [LoadBalancedSupertoneTtsAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/tts/LoadBalancedSupertoneTtsAdapter.java)
  - Queue size 추적: `tts.endpoint.queue.size` Gauge
  - Wait time 추적: `tts.endpoint.request.wait.time` Timer

- ✏️ Pipeline Sentence buffer 메트릭 추가
  - `pipeline.sentence.buffer.size` Gauge

**메트릭 예시**:
```promql
# TTS Endpoint Queue 크기
tts_endpoint_queue_size{endpoint="endpoint1"}

# Sentence Buffer 크기
pipeline_sentence_buffer_size
```

#### 1.3 파이프라인 병목 대시보드

**파일 생성**:
- ✨ [miyou-pipeline-bottleneck.json](monitoring/grafana/dashboards/miyou-pipeline-bottleneck.json) (신규)
  - 5 Rows, 12 패널
  - 자세한 내용은 [대시보드 4](#대시보드-4-miyou-pipeline-병목-분석) 참조

---

### Phase 1B: RAG 품질 모니터링 (2-3일) - CRITICAL

#### 1.4 Vector 검색 품질 메트릭

**목표**: Vector 검색 유사도, 중요도, 필터링 비율 추적

**파일 변경**:
- ✏️ [MemoryRetrievalService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/memory/service/MemoryRetrievalService.java)
  - `rankAndLimit()` 함수: Similarity score 보존
  - Pipeline Attributes에 `memorySimilarityScores` 추가
  - Candidate count vs Final count 추적

- ✏️ [SpringAiVectorDbAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/vector/SpringAiVectorDbAdapter.java)
  - `ScoredPoint.score` 노출

- ✨ [RagQualityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java) (신규)
  - `rag.memory.similarity.score` Histogram
  - `rag.memory.importance` Histogram
  - `rag.memory.filtered.count` Counter
  - `rag.memory.candidate.count` Counter

**메트릭 예시**:
```promql
# 평균 메모리 유사도 점수
avg(rag_memory_similarity_score)

# 필터링된 메모리 비율
sum(rag_memory_filtered_count) / sum(rag_memory_candidate_count) * 100
```

#### 1.5 메모리 추출 품질 메트릭

**목표**: 메모리 추출 빈도, 성공률, 품질 추적

**파일 변경**:
- ✏️ [MemoryExtractionService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/memory/service/MemoryExtractionService.java)
  - Extraction trigger 카운터
  - Success/Failure 카운터
  - Importance 분포 Histogram

- ✨ [MemoryExtractionMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java) (신규)
  - `memory.extraction.triggered` Counter
  - `memory.extraction.success` Counter
  - `memory.extraction.failure` Counter
  - `memory.extracted.importance` Histogram
  - `memory.extracted.count` Counter (by type)

**메트릭 예시**:
```promql
# 메모리 추출 빈도 (분당)
rate(memory_extraction_triggered[5m]) * 60

# 메모리 추출 성공률
sum(memory_extraction_success) / (sum(memory_extraction_success) + sum(memory_extraction_failure)) * 100
```

#### 1.6 RAG 품질 대시보드

**파일 생성**:
- ✨ [miyou-rag-quality.json](monitoring/grafana/dashboards/miyou-rag-quality.json) (신규)
  - 7 Rows, 15 패널
  - MongoDB 데이터소스 연동 (검색된 메모리 내용 Table)
  - 자세한 내용은 [대시보드 5](#대시보드-5-miyou-rag-품질-모니터링) 참조

---

### Phase 1C: 기존 계획 (1-2일) - HIGH

#### 1.7 LLM/대화 메트릭

**파일 생성**:
- ✨ [LlmMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/LlmMetricsConfiguration.java) (신규)
  - 토큰 사용량 Counter (prompt/completion)
  - 모델별 요청 Counter

- ✨ [ConversationMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/ConversationMetricsConfiguration.java) (신규)
  - Redis 기반 대화 카운터 Gauge
  - 활성 세션 Gauge

**파일 변경**:
- ✏️ LLM 클라이언트 수정: 토큰 카운터 증가

#### 1.8 Loki 로그 대시보드

**파일 생성**:
- ✨ [miyou-application-logs.json](monitoring/grafana/dashboards/miyou-application-logs.json) (신규)
  - 5 Rows, 8 패널
  - 로그 레벨 분포, 에러 추이, 파이프라인 로그, 느린 요청
  - 자세한 내용은 [대시보드 9](#대시보드-9-miyou-application-logs) 참조

---

### Phase 2: 비용 및 UX 모니터링 (2-3일) - MEDIUM

#### 2.1 비용 추적

**파일 생성**:
- ✨ [CostTrackingMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/CostTrackingMetricsConfiguration.java) (신규)
  - `llm.cost.usd` Counter
    - 계산: `(promptTokens * $0.003 + completionTokens * $0.015) / 1000` (Claude Sonnet 4.5)
    - Tags: `model`, `user_id`
  - `tts.cost.usd` Counter
    - 계산: 문자 수 기반 과금 (Supertone API 정책)

- ✨ [miyou-cost-usage.json](monitoring/grafana/dashboards/miyou-cost-usage.json) (신규)
  - 4 Rows, 10 패널
  - 자세한 내용은 [대시보드 6](#대시보드-6-miyou-비용-및-사용량-분석) 참조

**메트릭 예시**:
```promql
# 일일 총 비용
sum(increase(llm_cost_usd[1d])) + sum(increase(tts_cost_usd[1d]))

# 월간 누적 비용
sum(increase(llm_cost_usd[30d])) + sum(increase(tts_cost_usd[30d]))
```

#### 2.2 사용자 경험 메트릭

**파일 생성**:
- ✨ [UxMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/UxMetricsConfiguration.java) (신규)
  - `ux.response.latency` Histogram
    - Tags: `response_type` (first_token, first_audio, complete)
    - Percentiles: p50, p90, p95, p99
  - `ux.error.rate` Counter
    - Tags: `user_id`, `error_type` (llm_failure, tts_failure, timeout)

- ✨ [miyou-ux.json](monitoring/grafana/dashboards/miyou-ux.json) (신규)
  - 3 Rows, 8 패널
  - 자세한 내용은 [대시보드 7](#대시보드-7-miyou-사용자-경험-ux) 참조

**메트릭 예시**:
```promql
# 평균 첫 응답 시간 (TTFB)
avg(ux_response_latency{response_type="first_token"})

# 사용자 경험 에러율
sum(rate(ux_error_rate[5m])) / sum(rate(pipeline_executions_total[5m])) * 100
```

---

### Phase 3: 시스템 안정성 및 MongoDB 통합 (3-5일) - LOW~MEDIUM

#### 3.1 안정성 메트릭

**파일 생성**:
- ✨ [StabilityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/StabilityMetricsConfiguration.java) (신규)
  - `circuit.breaker.state` Gauge
    - Values: `0=CLOSED`, `1=OPEN`, `2=HALF_OPEN`
    - Tags: `component` (llm, tts, vectordb)
  - `retry.attempts` Counter
  - `retry.success` Counter
  - `timeout.occurred` Counter
    - Tags: `stage`, `timeout_threshold`

- ✨ [miyou-stability.json](monitoring/grafana/dashboards/miyou-stability.json) (신규)
  - 3 Rows, 7 패널
  - 자세한 내용은 [대시보드 8](#대시보드-8-miyou-시스템-안정성) 참조

**메트릭 예시**:
```promql
# Circuit Breaker 상태
circuit_breaker_state{component="llm"}

# Retry 성공률
sum(retry_success) / sum(retry_attempts) * 100
```

#### 3.2 MongoDB Exporter 추가

**목표**: MongoDB 성능 메트릭 수집

**파일 변경**:
- ✏️ [docker-compose.monitoring.yml](monitoring/docker-compose.yml)
  - MongoDB Exporter 서비스 추가:
    ```yaml
    mongodb-exporter:
      image: percona/mongodb_exporter:0.40
      command:
        - --mongodb.uri=mongodb://mongodb:27017
        - --collect-all
      ports:
        - "9216:9216"
    ```

- ✏️ [prometheus.yml](monitoring/prometheus/prometheus.yml)
  - MongoDB Exporter 스크랩 타겟 추가:
    ```yaml
    - job_name: 'mongodb'
      static_configs:
        - targets: ['mongodb-exporter:9216']
    ```

#### 3.3 MongoDB 데이터소스 (선택 사항)

**파일 변경**:
- ✏️ [datasources.yml](monitoring/grafana/provisioning/datasources/datasources.yml)
  - MongoDB 데이터소스 추가:
    ```yaml
    - name: MongoDB
      type: grafana-mongodb-datasource
      url: mongodb://mongodb:27017
      database: miyou_monitoring
    ```

**사용처**:
- RAG 품질 대시보드: 검색된 메모리 내용 Table
- RAG 품질 대시보드: 추출된 메모리 내용 Table

---

## 신규 메트릭

### 파이프라인 병목 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `pipeline.stage.gap.duration` | Timer | `from_stage`, `to_stage` | Stage 간 전환 시간 |
| `pipeline.sentence.buffer.size` | Gauge | - | Sentence buffer 크기 |
| `pipeline.data.size.bytes` | Gauge | `stage`, `data_type` | Stage별 데이터 크기 |
| `tts.endpoint.queue.size` | Gauge | `endpoint` | TTS 엔드포인트 Queue 크기 |
| `tts.endpoint.request.wait.time` | Timer | `endpoint` | TTS 요청 대기 시간 |

### RAG 품질 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `rag.memory.similarity.score` | Histogram | - | 메모리 유사도 점수 |
| `rag.memory.importance` | Histogram | - | 메모리 중요도 |
| `rag.memory.candidate.count` | Counter | - | 검색 후보 메모리 개수 |
| `rag.memory.filtered.count` | Counter | - | 필터링된 메모리 개수 |
| `rag.memory.count` | Gauge | `memory_type` | 검색된 메모리 개수 (타입별) |
| `rag.document.relevance.score` | Histogram | - | 문서 관련성 점수 |
| `rag.document.count` | Gauge | - | 검색된 문서 개수 |

### 메모리 추출 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `memory.extraction.triggered` | Counter | - | 메모리 추출 트리거 |
| `memory.extraction.success` | Counter | - | 메모리 추출 성공 |
| `memory.extraction.failure` | Counter | - | 메모리 추출 실패 |
| `memory.extracted.count` | Counter | `type` | 추출된 메모리 개수 (타입별) |
| `memory.extracted.importance` | Histogram | - | 추출된 메모리 중요도 |

### 비용 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `llm.cost.usd` | Counter | `model`, `user_id` | LLM 비용 (USD) |
| `tts.cost.usd` | Counter | `user_id` | TTS 비용 (USD) |
| `llm.tokens.prompt` | Counter | `model` | Prompt 토큰 사용량 |
| `llm.tokens.completion` | Counter | `model` | Completion 토큰 사용량 |

### 사용자 경험 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `ux.response.latency` | Histogram | `response_type` | 응답 시간 (first_token, first_audio, complete) |
| `ux.error.rate` | Counter | `user_id`, `error_type` | 에러율 |
| `conversation.turn.count` | Histogram | - | 대화 길이 (턴 수) |

### 시스템 안정성 메트릭

| 메트릭 | 타입 | Tags | 설명 |
|--------|------|------|------|
| `circuit.breaker.state` | Gauge | `component` | Circuit Breaker 상태 (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `retry.attempts` | Counter | `operation` | Retry 시도 |
| `retry.success` | Counter | `operation` | Retry 성공 |
| `timeout.occurred` | Counter | `stage`, `timeout_threshold` | Timeout 발생 |

---

## 대시보드 구성

### 전체 대시보드 목록

| # | 대시보드 이름 | UID | Row 수 | 패널 수 | 상태 | 주요 목적 |
|---|---------------|-----|--------|---------|------|----------|
| 1 | MIYOU APM 개요 | miyou-overview | 6 | 18 | 기존 | Golden Signals, HTTP, JVM, TTS 요약 |
| 2 | MIYOU JVM & 인프라 | miyou-jvm-infra | 5 | 16 | 기존 | JVM 상세 메트릭 |
| 3 | MIYOU TTS 로드밸런서 | miyou-tts | 4 | 9 | 기존 | TTS 엔드포인트 상세 |
| 4 | MIYOU Pipeline 병목 분석 | miyou-pipeline-bottleneck | 5 | 12 | 신규 | Stage별 성능, Gap, Backpressure |
| 5 | MIYOU RAG 품질 모니터링 | miyou-rag-quality | 7 | 15 | 신규 | Vector 검색, 메모리 품질, 내용 확인 |
| 6 | MIYOU 비용 및 사용량 | miyou-cost-usage | 4 | 10 | 신규 | LLM/TTS 비용 추적 |
| 7 | MIYOU 사용자 경험 (UX) | miyou-ux | 3 | 8 | 신규 | 응답 시간, 에러율 |
| 8 | MIYOU 시스템 안정성 | miyou-stability | 3 | 7 | 신규 | Circuit Breaker, Retry, Timeout |
| 9 | MIYOU Application Logs | miyou-application-logs | 5 | 8 | 신규 | 애플리케이션 로그 분석 |

**총 대시보드**: 9개
**총 패널**: 103개

---

### 대시보드 4: MIYOU Pipeline 병목 분석

**파일**: [miyou-pipeline-bottleneck.json](monitoring/grafana/dashboards/miyou-pipeline-bottleneck.json)
**UID**: `miyou-pipeline-bottleneck`

#### Row 1: 파이프라인 전체 KPI

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 평균 실행 시간 | Stat | `rate(pipeline_duration_sum[5m]) / rate(pipeline_duration_count[5m])` | 5분 평균 |
| 처리량 | Stat | `rate(pipeline_executions_total[1m]) * 60` | executions/min |
| 성공률 | Stat | `sum(rate(pipeline_executions_total{status="COMPLETED"}[5m])) / sum(rate(pipeline_executions_total[5m])) * 100` | % |
| 실행 중 파이프라인 | Stat | `pipeline_active_count` | 현재 실행 중 |

#### Row 2: Stage별 실행 시간 분석

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Stage별 실행 시간 분포 | Heatmap | `pipeline_stage_duration_bucket` | 좌측 50%, X축: 시간, Y축: Duration |
| Stage별 p95 실행 시간 추이 | TimeSeries | `histogram_quantile(0.95, pipeline_stage_duration_bucket) by (stage)` | 우측 50% |

#### Row 3: Stage 간 Gap 분석 (병목 탐지)

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Stage 전환 평균 Gap | Bar Gauge | `avg(pipeline_stage_gap_duration) by (from_stage, to_stage)` | 좌측 40%, 내림차순 정렬 |
| Top 3 Gap 추이 | TimeSeries | `topk(3, pipeline_stage_gap_duration{quantile="0.95"})` | 우측 60% |

#### Row 4: Backpressure 지표

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| TTS Endpoint Queue 크기 | Gauge | `tts_endpoint_queue_size` | 엔드포인트별 |
| Sentence Buffer 크기 | Gauge | `pipeline_sentence_buffer_size` | - |
| Reactor Event Loop Pending Tasks | TimeSeries | `reactor_netty_eventloop_pending_tasks` | - |

#### Row 5: 메모리 사용량 추정

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Stage별 데이터 크기 | TimeSeries | `pipeline_data_size_bytes by (stage, data_type)` | 좌측 50% |
| Pipeline 실행 중 Heap 증가량 | TimeSeries | `increase(jvm_memory_used_bytes{area="heap"}[30s])` | 우측 50% |

---

### 대시보드 5: MIYOU RAG 품질 모니터링

**파일**: [miyou-rag-quality.json](monitoring/grafana/dashboards/miyou-rag-quality.json)
**UID**: `miyou-rag-quality`

#### Row 1: RAG 품질 KPI

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 평균 메모리 유사도 점수 | Stat | `avg(rag_memory_similarity_score)` | - |
| 평균 메모리 중요도 | Stat | `avg(rag_memory_importance)` | - |
| 평균 검색 메모리 개수 | Stat | `avg(rag_memory_count)` | - |
| 필터링된 메모리 비율 | Stat | `sum(rag_memory_filtered_count) / sum(rag_memory_candidate_count) * 100` | % |

#### Row 2: 메모리 유사도 분포

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 유사도 점수 분포 | Heatmap | `rag_memory_similarity_score_bucket` | 좌측 50%, X축: 시간, Y축: Score |
| 현재 유사도 점수 분포 | Histogram | `histogram_quantile([0.25, 0.5, 0.75, 0.95], rag_memory_similarity_score_bucket)` | 우측 50% |

#### Row 3: 메모리 중요도 및 타입 분석

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 검색된 메모리 타입별 개수 | TimeSeries | `rag_memory_count by (memory_type)` | 좌측 50%, EXPERIENTIAL/FACTUAL |
| 메모리 중요도 분포 | Gauge | `histogram_quantile([0.5, 0.9], rag_memory_importance_bucket)` | 우측 50% |

#### Row 4: 메모리 검색 내용 (MongoDB 연동)

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 최근 검색된 메모리 Top 10 | Table | MongoDB | 아래 참조 | 전체 너비 |

**MongoDB 쿼리**:
```javascript
db.performance_metrics.aggregate([
  { $match: { startedAt: { $gte: new Date(Date.now() - 3600000) } } },
  { $unwind: "$stages.memoryRetrieval.memories" },
  { $project: {
      content: "$stages.memoryRetrieval.memories.content",
      similarity: "$stages.memoryRetrieval.memorySimilarityScores",
      importance: "$stages.memoryRetrieval.memoryImportanceScores"
  }},
  { $limit: 10 }
])
```

**컬럼**: 내용 미리보기, 유사도, 중요도, 타입

#### Row 5: 문서 검색 품질

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 문서 관련성 점수 추이 | TimeSeries | `rag_document_relevance_score` | 좌측 50% |
| 평균 검색 문서 개수 | Stat | `avg(rag_document_count)` | 우측 25% |
| 평균 문서 관련성 점수 | Stat | `avg(rag_document_relevance_score)` | 우측 25% |

#### Row 6: 메모리 추출 품질

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 메모리 추출 빈도 | TimeSeries | `rate(memory_extraction_triggered[5m]) * 60` | 좌측 40%, extractions/min |
| 메모리 추출 성공률 | Stat | `sum(memory_extraction_success) / (sum(memory_extraction_success) + sum(memory_extraction_failure)) * 100` | 중간 20%, % |
| 추출된 메모리 타입 분포 | Pie Chart | `sum(memory_extracted_count) by (type)` | 우측 40% |

#### Row 7: 추출된 메모리 내용 (MongoDB 연동)

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 최근 추출된 메모리 목록 | Table | MongoDB | 아래 참조 | 전체 너비 |

**MongoDB 쿼리**:
```javascript
db.extracted_memories.find()
  .sort({ createdAt: -1 })
  .limit(20)
  .project({ content: 1, type: 1, importance: 1, conversationId: 1 })
```

**컬럼**: 내용, 타입, 중요도, 출처 대화 ID

---

### 대시보드 6: MIYOU 비용 및 사용량 분석

**파일**: [miyou-cost-usage.json](monitoring/grafana/dashboards/miyou-cost-usage.json)
**UID**: `miyou-cost-usage`

#### Row 1: 비용 KPI

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 일일 총 비용 | Stat | `sum(increase(llm_cost_usd[1d])) + sum(increase(tts_cost_usd[1d]))` | USD |
| 월간 누적 비용 | Stat | `sum(increase(llm_cost_usd[30d])) + sum(increase(tts_cost_usd[30d]))` | USD |
| 월간 비용 목표 대비 진행률 | Gauge | `(sum(increase(llm_cost_usd[30d])) + sum(increase(tts_cost_usd[30d]))) / $monthly_budget * 100` | % |

#### Row 2: 비용 추이

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 일일 비용 추이 | TimeSeries | `sum(increase(llm_cost_usd[1d]))`, `sum(increase(tts_cost_usd[1d]))` | Stacked Area, LLM vs TTS |

#### Row 3: 토큰 사용량

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 시간대별 토큰 사용량 | TimeSeries | `rate(llm_tokens_prompt[5m])`, `rate(llm_tokens_completion[5m])` | Prompt vs Completion |
| 일일 총 토큰 사용량 | Stat | `sum(increase(llm_tokens_total[1d]))` | - |

#### Row 4: 사용자별 비용 분석

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 사용자별 비용 Top 10 | Table | `topk(10, sum(llm_cost_usd + tts_cost_usd) by (user_id))` | - |
| 사용자별 비용 분포 | Pie Chart | `sum(llm_cost_usd + tts_cost_usd) by (user_id)` | - |

---

### 대시보드 7: MIYOU 사용자 경험 (UX)

**파일**: [miyou-ux.json](monitoring/grafana/dashboards/miyou-ux.json)
**UID**: `miyou-ux`

#### Row 1: UX KPI

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 평균 첫 응답 시간 (TTFB) | Stat | `avg(ux_response_latency{response_type="first_token"})` | - |
| 평균 전체 응답 시간 | Stat | `avg(ux_response_latency{response_type="complete"})` | - |
| 사용자 경험 에러율 | Stat | `sum(rate(ux_error_rate[5m])) / sum(rate(pipeline_executions_total[5m])) * 100` | % |

#### Row 2: 응답 시간 분포

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 응답 시간 분포 (TTFB) | Heatmap | `ux_response_latency_bucket{response_type="first_token"}` | - |

#### Row 3: 에러 분석

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| 에러율 추이 | TimeSeries | `rate(ux_error_rate[5m]) by (error_type)` | 에러 타입별 |
| Top 에러 타입 | Table | `topk(10, sum(ux_error_rate) by (error_type))` | - |

---

### 대시보드 8: MIYOU 시스템 안정성

**파일**: [miyou-stability.json](monitoring/grafana/dashboards/miyou-stability.json)
**UID**: `miyou-stability`

#### Row 1: Circuit Breaker 상태

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Circuit Breaker 상태 | Gauge | `circuit_breaker_state by (component)` | LLM, TTS, VectorDB (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |

#### Row 2: Retry 성공률

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Retry 시도 및 성공 추이 | TimeSeries | `rate(retry_attempts[5m])`, `rate(retry_success[5m])` | - |
| Retry 성공률 | Stat | `sum(retry_success) / sum(retry_attempts) * 100` | % |

#### Row 3: Timeout 분석

| 패널 | 타입 | 쿼리 | 설명 |
|------|------|------|------|
| Timeout 발생률 | TimeSeries | `rate(timeout_occurred[5m]) by (stage)` | Stage별 |

---

### 대시보드 9: MIYOU Application Logs

**파일**: [miyou-application-logs.json](monitoring/grafana/dashboards/miyou-application-logs.json)
**UID**: `miyou-application-logs`

#### Row 1: 로그 레벨 분포

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 로그 레벨 카운트 | Pie Chart | Loki | `sum(count_over_time({job="docker-logs"}[5m])) by (level)` | - |

#### Row 2: 에러 추이

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 에러 로그 추이 | TimeSeries | Loki | `count_over_time({job="docker-logs"} \|~ "(?i)error"[5m])` | - |

#### Row 3: Top 에러 메시지

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| Top 에러 메시지 | Table | Loki | `topk(10, {job="docker-logs"} \|~ "(?i)error")` | - |

#### Row 4: 파이프라인 로그

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 파이프라인 완료 로그 | Logs | Loki | `{job="docker-logs"} \|~ "(?i)pipeline.*completed"` | - |

#### Row 5: 느린 요청 로그

| 패널 | 타입 | Data Source | 쿼리 | 설명 |
|------|------|-------------|------|------|
| 느린 요청 (>5s) | Logs | Loki | `{job="docker-logs"} \|~ "duration.*[5-9][0-9]{3}\|[1-9][0-9]{4}"` | - |

---

## 파일 변경 목록

### 신규 생성 파일

#### Configuration 파일 (7개)

| 파일 | 용도 | 우선순위 |
|------|------|----------|
| [PipelineMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java) | 파이프라인 Stage별 메트릭 (duration, gap, backpressure) | CRITICAL |
| [RagQualityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java) | RAG 품질 메트릭 (similarity, importance, filtering) | CRITICAL |
| [MemoryExtractionMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java) | 메모리 추출 메트릭 (triggers, success, quality) | HIGH |
| [LlmMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/LlmMetricsConfiguration.java) | LLM 토큰 메트릭 | HIGH |
| [ConversationMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/ConversationMetricsConfiguration.java) | 대화 카운터 메트릭 | HIGH |
| [CostTrackingMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/CostTrackingMetricsConfiguration.java) | LLM/TTS 비용 계산 메트릭 | MEDIUM |
| [UxMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/UxMetricsConfiguration.java) | 사용자 경험 메트릭 (latency, errors) | MEDIUM |
| [StabilityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/StabilityMetricsConfiguration.java) | Circuit Breaker, Retry, Timeout 메트릭 | LOW |

#### 대시보드 파일 (6개)

| 파일 | 용도 | 우선순위 |
|------|------|----------|
| [miyou-pipeline-bottleneck.json](monitoring/grafana/dashboards/miyou-pipeline-bottleneck.json) | 파이프라인 병목 대시보드 (5 Rows, 12 패널) | CRITICAL |
| [miyou-rag-quality.json](monitoring/grafana/dashboards/miyou-rag-quality.json) | RAG 품질 대시보드 (7 Rows, 15 패널) | CRITICAL |
| [miyou-application-logs.json](monitoring/grafana/dashboards/miyou-application-logs.json) | 애플리케이션 로그 대시보드 (5 Rows, 8 패널) | HIGH |
| [miyou-cost-usage.json](monitoring/grafana/dashboards/miyou-cost-usage.json) | 비용 대시보드 (4 Rows, 10 패널) | MEDIUM |
| [miyou-ux.json](monitoring/grafana/dashboards/miyou-ux.json) | UX 대시보드 (3 Rows, 8 패널) | MEDIUM |
| [miyou-stability.json](monitoring/grafana/dashboards/miyou-stability.json) | 안정성 대시보드 (3 Rows, 7 패널) | LOW |

### 수정 파일

#### 애플리케이션 코드 (6개)

| 파일 | 변경 내용 | 우선순위 |
|------|----------|----------|
| [DialoguePipelineTracker.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/pipeline/DialoguePipelineTracker.java) | Stage 간 Gap 계산, Attributes에 품질 점수 추가 | CRITICAL |
| [MemoryRetrievalService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/memory/service/MemoryRetrievalService.java) | Similarity score 보존, Candidate count 추적 | CRITICAL |
| [SpringAiVectorDbAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/vector/SpringAiVectorDbAdapter.java) | Vector search score 노출 | CRITICAL |
| [PersistentPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/reporter/PersistentPipelineMetricsReporter.java) | 품질 메트릭 MongoDB 저장, Micrometer 등록 | CRITICAL |
| [MemoryExtractionService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/domain/memory/service/MemoryExtractionService.java) | 추출 메트릭 수집 (trigger, success, quality) | HIGH |
| [LoadBalancedSupertoneTtsAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/tts/LoadBalancedSupertoneTtsAdapter.java) | Queue size, Wait time 메트릭 추가 | MEDIUM |

#### 인프라 파일 (3개)

| 파일 | 변경 내용 | 우선순위 |
|------|----------|----------|
| [docker-compose.monitoring.yml](monitoring/docker-compose.yml) | MongoDB Exporter 서비스 추가 (Phase 3) | MEDIUM |
| [prometheus.yml](monitoring/prometheus/prometheus.yml) | MongoDB Exporter 스크랩 타겟 추가 (Phase 3) | MEDIUM |
| [datasources.yml](monitoring/grafana/provisioning/datasources/datasources.yml) | MongoDB 데이터소스 추가 (Phase 3, 선택 사항) | LOW |

---

## 검증 방법

### Phase 1A 검증: 파이프라인 병목 분석

#### 1. Prometheus 메트릭 노출 확인

```bash
# 파이프라인 Gap 메트릭
curl http://localhost:8080/actuator/prometheus | grep "pipeline_stage_gap"

# Backpressure 메트릭
curl http://localhost:8080/actuator/prometheus | grep "tts_endpoint_queue_size"
curl http://localhost:8080/actuator/prometheus | grep "pipeline_sentence_buffer_size"
```

#### 2. Grafana 쿼리 테스트

```promql
# Stage 전환 평균 Gap
avg(pipeline_stage_gap_duration_seconds) by (from_stage, to_stage)

# TTS Queue 크기
tts_endpoint_queue_size{endpoint="endpoint1"}
```

#### 3. 대시보드 확인

- Grafana UI → Dashboards → MIYOU Pipeline 병목 분석
- 모든 패널 데이터 로딩 확인
- Stage Gap Bar Gauge에서 병목 지점 확인

---

### Phase 1B 검증: RAG 품질 모니터링

#### 1. Prometheus 메트릭 노출 확인

```bash
# RAG 품질 메트릭
curl http://localhost:8080/actuator/prometheus | grep "rag_memory_similarity"
curl http://localhost:8080/actuator/prometheus | grep "rag_memory_importance"
curl http://localhost:8080/actuator/prometheus | grep "memory_extraction"
```

#### 2. Grafana 쿼리 테스트

```promql
# 평균 메모리 유사도 점수
avg(rag_memory_similarity_score)

# 메모리 추출 성공률
sum(memory_extraction_success) / (sum(memory_extraction_success) + sum(memory_extraction_failure)) * 100
```

#### 3. MongoDB 쿼리 테스트

```javascript
// 최근 검색된 메모리
db.performance_metrics.aggregate([
  { $match: { startedAt: { $gte: new Date(Date.now() - 3600000) } } },
  { $unwind: "$stages.memoryRetrieval.memories" },
  { $project: {
      content: "$stages.memoryRetrieval.memories.content",
      similarity: "$stages.memoryRetrieval.memorySimilarityScores"
  }},
  { $limit: 10 }
])
```

#### 4. 대시보드 확인

- Grafana UI → Dashboards → MIYOU RAG 품질 모니터링
- Row 4: 최근 검색된 메모리 Table 확인
- Row 7: 최근 추출된 메모리 Table 확인

---

### Phase 1C 검증: 기존 계획

#### 1. Prometheus 메트릭 노출 확인

```bash
# LLM 메트릭
curl http://localhost:8080/actuator/prometheus | grep "llm_tokens"

# 대화 메트릭
curl http://localhost:8080/actuator/prometheus | grep "conversation_"
```

#### 2. Loki 로그 쿼리 테스트

```logql
# 에러 로그
{job="docker-logs", container="miyou-dialogue-app"} |~ "(?i)error"

# 파이프라인 로그
{job="docker-logs"} |~ "(?i)pipeline.*completed"
```

#### 3. 대시보드 확인

- Grafana UI → Dashboards → MIYOU Application Logs
- 모든 로그 패널 데이터 로딩 확인

---

### Phase 2 검증: 비용 및 UX 모니터링

#### 1. Prometheus 메트릭 노출 확인

```bash
# 비용 메트릭
curl http://localhost:8080/actuator/prometheus | grep "llm_cost_usd"
curl http://localhost:8080/actuator/prometheus | grep "tts_cost_usd"

# UX 메트릭
curl http://localhost:8080/actuator/prometheus | grep "ux_response_latency"
curl http://localhost:8080/actuator/prometheus | grep "ux_error_rate"
```

#### 2. Grafana 쿼리 테스트

```promql
# 일일 총 비용
sum(increase(llm_cost_usd[1d])) + sum(increase(tts_cost_usd[1d]))

# 평균 첫 응답 시간
avg(ux_response_latency{response_type="first_token"})
```

#### 3. 대시보드 확인

- Grafana UI → Dashboards → MIYOU 비용 및 사용량
- Grafana UI → Dashboards → MIYOU 사용자 경험 (UX)

---

### Phase 3 검증: 시스템 안정성 및 MongoDB 통합

#### 1. MongoDB Exporter 메트릭 확인

```bash
curl http://localhost:9216/metrics | grep "mongodb_"
```

#### 2. Prometheus 타겟 확인

```bash
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | select(.job=="mongodb")'
```

#### 3. Grafana MongoDB 데이터소스 테스트

- Grafana UI → Configuration → Data Sources → MongoDB
- "Test" 버튼 클릭
- 성공 메시지 확인

#### 4. 안정성 메트릭 확인

```bash
# Circuit Breaker 메트릭
curl http://localhost:8080/actuator/prometheus | grep "circuit_breaker_state"

# Retry 메트릭
curl http://localhost:8080/actuator/prometheus | grep "retry_"

# Timeout 메트릭
curl http://localhost:8080/actuator/prometheus | grep "timeout_occurred"
```

#### 5. 대시보드 확인

- Grafana UI → Dashboards → MIYOU 시스템 안정성

---

## 데이터 흐름도

### Phase 1 완료 후

```
애플리케이션 코드
├─ DialoguePipelineTracker
│  ├─ MongoDB 저장 (상세 메트릭)
│  └─ Micrometer 등록 (집계 메트릭) → Prometheus → Grafana
│
├─ MemoryRetrievalService
│  ├─ MongoDB 저장 (검색된 메모리 내용)
│  └─ Micrometer 등록 (품질 메트릭) → Prometheus → Grafana
│
├─ MemoryExtractionService
│  ├─ MongoDB 저장 (추출된 메모리 내용)
│  └─ Micrometer 등록 (추출 메트릭) → Prometheus → Grafana
│
├─ LlmClient
│  └─ Micrometer Counter → Prometheus → Grafana
│
├─ ConversationService
│  └─ Redis 조회 → Micrometer Gauge → Prometheus → Grafana
│
└─ 애플리케이션 로그
   └─ Docker → Alloy → Loki → Grafana
```

### Phase 2 완료 후

```
애플리케이션 코드
├─ CostTrackingService
│  └─ Micrometer Counter (LLM/TTS 비용) → Prometheus → Grafana
│
├─ UxMetricsService
│  └─ Micrometer Histogram (응답 시간, 에러율) → Prometheus → Grafana
│
└─ (나머지 동일)
```

### Phase 3 완료 후

```
애플리케이션 코드
├─ MongoDB
│  ├─ MongoDB Exporter → Prometheus → Grafana (DB 성능 메트릭)
│  └─ MongoDB Datasource → Grafana (비즈니스 메트릭, 직접 쿼리)
│
├─ StabilityMetricsService
│  └─ Micrometer Gauge/Counter (Circuit Breaker, Retry, Timeout) → Prometheus → Grafana
│
└─ (나머지 동일)
```

---

## 예상 결과

### Phase 1 완료 후 시각화 가능한 메트릭

| 카테고리 | 메트릭 | 시각화 방법 | 사용자 요구사항 충족 |
|----------|--------|-------------|---------------------|
| 파이프라인 병목 | Stage 간 Gap | TimeSeries, Bar Gauge | ✅ 데이터 흐름 속도 |
| 파이프라인 병목 | Backpressure (Queue size) | Gauge | ✅ 병목 현상 탐지 |
| 파이프라인 병목 | Stage별 데이터 크기 | TimeSeries | ✅ 메모리 사용량 추정 |
| RAG 품질 | Vector 유사도 점수 | Heatmap, Histogram | ✅ RAG 검색 품질 |
| RAG 품질 | 검색된 메모리 내용 | Table (MongoDB) | ✅ "어떤 메모리 검색" |
| RAG 품질 | 메모리 중요도 분포 | Histogram | ✅ 메모리 품질 |
| 메모리 추출 | 추출된 메모리 내용 | Table (MongoDB) | ✅ "어떤 메모리 추출" |
| 메모리 추출 | 추출 성공률 | Stat | ✅ 메모리 추출 품질 |
| LLM | 토큰 사용량 (prompt/completion) | Counter, Rate | 운영 효율성 |
| 대화 | 일일 대화 수 | Gauge, TimeSeries | 운영 효율성 |
| 로그 | 레벨별 로그 카운트 | Gauge, Pie Chart | 운영 효율성 |
| 로그 | 에러 추이 | TimeSeries | 운영 효율성 |

### Phase 2 완료 후 추가 메트릭

| 카테고리 | 메트릭 | 시각화 방법 |
|----------|--------|-------------|
| 비용 | LLM/TTS 비용 | TimeSeries, Stat |
| UX | 응답 시간 분포 | Heatmap |
| UX | 에러율 | TimeSeries, Stat |

### Phase 3 완료 후 추가 메트릭

| 카테고리 | 메트릭 | 시각화 방법 |
|----------|--------|-------------|
| MongoDB | DB 성능 메트릭 | TimeSeries, Gauge |
| 안정성 | Circuit Breaker 상태 | Gauge |
| 안정성 | Retry 성공률 | Stat |
| 안정성 | Timeout 발생률 | TimeSeries |

---

## 우선순위 결정 지침

### 즉시 구현 권장 (Phase 1)

✅ **파이프라인 메트릭 Micrometer 등록**
✅ **RAG 품질 메트릭**
✅ **메모리 추출 메트릭**
✅ **로그 대시보드 생성**

**이유**:
- 코드 수정 최소
- 즉시 가시성 확보
- 기존 인프라만 활용
- 사용자 요구사항 직접 충족

### 중기 구현 (Phase 2)

⚠️ **비용 추적**
⚠️ **UX 메트릭**

**이유**:
- 운영 효율성 개선
- 비즈니스 인사이트 제공

### 장기 구현 (Phase 3)

📊 **MongoDB Exporter 추가**
📊 **안정성 메트릭**

**이유**:
- 인프라 추가 필요
- Phase 1/2 완료 후 데이터 축적 필요
- 고급 분석 기능 (급하지 않음)

---

## 참고 자료

### Prometheus

- [Prometheus Metrics 네이밍 가이드](https://prometheus.io/docs/practices/naming/)
- [Prometheus Query Functions](https://prometheus.io/docs/prometheus/latest/querying/functions/)

### Grafana

- [Grafana Loki LogQL](https://grafana.com/docs/loki/latest/logql/)
- [Grafana MongoDB Plugin](https://grafana.com/grafana/plugins/grafana-mongodb-datasource/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)

### Spring Boot

- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Micrometer Custom Metrics](https://micrometer.io/docs/concepts)
  - [Gauges](https://micrometer.io/docs/concepts#_gauges)
  - [Timers](https://micrometer.io/docs/concepts#_timers)
  - [Histograms and Percentiles](https://micrometer.io/docs/concepts#_histograms_and_percentiles)

### MongoDB

- [MongoDB Exporter (Percona)](https://github.com/percona/mongodb_exporter)
- [MongoDB Grafana Datasource](https://grafana.com/grafana/plugins/grafana-mongodb-datasource/)

---

## 구현 체크리스트

### Phase 1A: 파이프라인 병목 분석

- [ ] `DialoguePipelineTracker.java` Stage Gap 계산 로직 추가
- [ ] `PipelineMetricsConfiguration.java` 생성
- [ ] `LoadBalancedSupertoneTtsAdapter.java` Queue size 메트릭 추가
- [ ] Sentence buffer 메트릭 추가
- [ ] `miyou-pipeline-bottleneck.json` 대시보드 생성
- [ ] Prometheus 메트릭 노출 확인
- [ ] Grafana 대시보드 테스트

### Phase 1B: RAG 품질 모니터링

- [ ] `MemoryRetrievalService.java` Similarity score 보존
- [ ] `SpringAiVectorDbAdapter.java` Score 노출
- [ ] `RagQualityMetricsConfiguration.java` 생성
- [ ] `MemoryExtractionService.java` 추출 메트릭 수집
- [ ] `MemoryExtractionMetricsConfiguration.java` 생성
- [ ] `miyou-rag-quality.json` 대시보드 생성
- [ ] MongoDB 데이터소스 연동
- [ ] MongoDB 쿼리 테스트
- [ ] Grafana 대시보드 테스트

### Phase 1C: 기존 계획

- [ ] `LlmMetricsConfiguration.java` 생성
- [ ] `ConversationMetricsConfiguration.java` 생성
- [ ] LLM 클라이언트 토큰 카운터 추가
- [ ] `miyou-application-logs.json` 대시보드 생성
- [ ] Loki 로그 쿼리 테스트
- [ ] Grafana 대시보드 테스트

### Phase 2: 비용 및 UX 모니터링

- [ ] `CostTrackingMetricsConfiguration.java` 생성
- [ ] `UxMetricsConfiguration.java` 생성
- [ ] `miyou-cost-usage.json` 대시보드 생성
- [ ] `miyou-ux.json` 대시보드 생성
- [ ] Grafana 대시보드 테스트

### Phase 3: 시스템 안정성 및 MongoDB 통합

- [ ] `StabilityMetricsConfiguration.java` 생성
- [ ] `miyou-stability.json` 대시보드 생성
- [ ] `docker-compose.monitoring.yml` MongoDB Exporter 추가
- [ ] `prometheus.yml` MongoDB 타겟 추가
- [ ] `datasources.yml` MongoDB 데이터소스 추가 (선택)
- [ ] MongoDB Exporter 메트릭 확인
- [ ] Grafana 대시보드 테스트

---

## 문의 및 지원

구현 중 문제가 발생하거나 질문이 있을 경우:

1. 각 Phase의 검증 방법을 참고하여 단계별로 확인
2. Prometheus `/metrics` 엔드포인트에서 메트릭 노출 여부 확인
3. Grafana Query Inspector로 쿼리 오류 디버깅
4. MongoDB 연결 및 쿼리 성능 측정

---

**최종 업데이트**: 2026-02-16
**문서 버전**: 1.0
**승인 상태**: ✅ 사용자 승인 완료
