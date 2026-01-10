# Grafana 모니터링 구현 계획

## 목표

RAG 파이프라인에서 수집된 메트릭을 Grafana 대시보드로 시각화하여 실시간 모니터링 및 성능 분석을 가능하게 한다.

---

## 아키텍처 개요

```
[Spring Boot App]
      ↓
[Micrometer Registry]
      ↓
[Prometheus Endpoint] (/actuator/prometheus)
      ↓
[Prometheus Server] (스크래핑)
      ↓
[Grafana] (대시보드)
```

---

## Phase 1: 의존성 및 설정

### 1.1 Gradle 의존성 추가

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 1.2 Application 설정

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
    distribution:
      percentiles-histogram:
        http.server.requests: true
        dialogue.stage.duration: true
```

### 1.3 Docker Compose 추가

```yaml
# docker-compose.yml (기존 파일에 추가)
services:
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
    networks:
      - rag-network

  grafana:
    image: grafana/grafana:10.2.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards
    depends_on:
      - prometheus
    networks:
      - rag-network

volumes:
  prometheus-data:
  grafana-data:

networks:
  rag-network:
    driver: bridge
```

### 1.4 Prometheus 설정 파일

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-rag'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
        labels:
          application: 'webflux-dialogue'
          environment: 'local'
```

---

## Phase 2: Micrometer 메트릭 수집 구현

### 2.1 MicrometerPipelineMetricsReporter 구현

```java
// infrastructure/monitoring/adapter/MicrometerPipelineMetricsReporter.java
package com.study.webflux.rag.infrastructure.monitoring.adapter;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.application.monitoring.monitor.PipelineMetricsReporter;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class MicrometerPipelineMetricsReporter implements PipelineMetricsReporter {

    private final MeterRegistry meterRegistry;

    @Override
    public void report(DialoguePipelineTracker.PipelineSummary summary) {
        recordPipelineMetrics(summary);
        recordStageMetrics(summary);
        recordBusinessMetrics(summary);
    }

    private void recordPipelineMetrics(DialoguePipelineTracker.PipelineSummary summary) {
        String status = summary.status().toString().toLowerCase();

        Timer.builder("dialogue.pipeline.duration")
            .tag("status", status)
            .description("Total pipeline execution time")
            .register(meterRegistry)
            .record(summary.durationMillis(), TimeUnit.MILLISECONDS);

        Counter.builder("dialogue.pipeline.total")
            .tag("status", status)
            .description("Total number of pipeline executions")
            .register(meterRegistry)
            .increment();

        if (summary.firstResponseLatencyMillis() != null) {
            Timer.builder("dialogue.pipeline.first_response")
                .tag("status", status)
                .description("Time to first response (TTFB)")
                .register(meterRegistry)
                .record(summary.firstResponseLatencyMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void recordStageMetrics(DialoguePipelineTracker.PipelineSummary summary) {
        summary.stages().forEach(stage -> {
            String stageName = stage.stage().toString().toLowerCase();
            String stageStatus = stage.status().toString().toLowerCase();

            Timer.builder("dialogue.stage.duration")
                .tag("stage", stageName)
                .tag("status", stageStatus)
                .description("Stage execution time")
                .register(meterRegistry)
                .record(stage.durationMillis(), TimeUnit.MILLISECONDS);

            recordStageAttributes(stage);
        });
    }

    private void recordStageAttributes(DialoguePipelineTracker.StageSnapshot stage) {
        String stageName = stage.stage().toString().toLowerCase();

        if (stage.stage() == DialoguePipelineStage.LLM_COMPLETION) {
            Object tokens = stage.attributes().get("totalTokens");
            if (tokens instanceof Number) {
                String model = String.valueOf(stage.attributes().getOrDefault("model", "unknown"));
                Counter.builder("llm.tokens.total")
                    .tag("model", model)
                    .tag("stage", stageName)
                    .description("Total LLM tokens consumed")
                    .register(meterRegistry)
                    .increment(((Number) tokens).doubleValue());
            }

            Object promptTokens = stage.attributes().get("promptTokens");
            if (promptTokens instanceof Number) {
                String model = String.valueOf(stage.attributes().getOrDefault("model", "unknown"));
                Counter.builder("llm.tokens.prompt")
                    .tag("model", model)
                    .description("LLM prompt tokens")
                    .register(meterRegistry)
                    .increment(((Number) promptTokens).doubleValue());
            }

            Object completionTokens = stage.attributes().get("completionTokens");
            if (completionTokens instanceof Number) {
                String model = String.valueOf(stage.attributes().getOrDefault("model", "unknown"));
                Counter.builder("llm.tokens.completion")
                    .tag("model", model)
                    .description("LLM completion tokens")
                    .register(meterRegistry)
                    .increment(((Number) completionTokens).doubleValue());
            }
        }

        if (stage.stage() == DialoguePipelineStage.MEMORY_RETRIEVAL) {
            Object memoryCount = stage.attributes().get("memoryCount");
            if (memoryCount instanceof Number) {
                Counter.builder("retrieval.memory.count")
                    .description("Number of memories retrieved")
                    .register(meterRegistry)
                    .increment(((Number) memoryCount).doubleValue());
            }
        }

        if (stage.stage() == DialoguePipelineStage.RETRIEVAL) {
            Object docCount = stage.attributes().get("documentCount");
            if (docCount instanceof Number) {
                Counter.builder("retrieval.document.count")
                    .description("Number of documents retrieved")
                    .register(meterRegistry)
                    .increment(((Number) docCount).doubleValue());
            }
        }

        if (stage.stage() == DialoguePipelineStage.TTS_SYNTHESIS) {
            Object audioChunks = stage.attributes().get("audioChunks");
            if (audioChunks instanceof Number) {
                Counter.builder("tts.audio.chunks")
                    .description("Number of audio chunks generated")
                    .register(meterRegistry)
                    .increment(((Number) audioChunks).doubleValue());
            }

            Object audioLength = stage.attributes().get("audioLengthMillis");
            if (audioLength instanceof Number) {
                DistributionSummary.builder("tts.audio.length")
                    .baseUnit("milliseconds")
                    .description("Audio length in milliseconds")
                    .register(meterRegistry)
                    .record(((Number) audioLength).doubleValue());
            }
        }

        if (stage.stage() == DialoguePipelineStage.SENTENCE_ASSEMBLY) {
            Object sentenceCount = stage.attributes().get("sentenceCount");
            if (sentenceCount instanceof Number) {
                DistributionSummary.builder("llm.sentence.count")
                    .description("Number of sentences assembled")
                    .register(meterRegistry)
                    .record(((Number) sentenceCount).doubleValue());
            }
        }
    }

    private void recordBusinessMetrics(DialoguePipelineTracker.PipelineSummary summary) {
        Object inputLength = summary.attributes().get("input.length");
        if (inputLength instanceof Number) {
            DistributionSummary.builder("dialogue.input.length")
                .description("User input text length")
                .register(meterRegistry)
                .record(((Number) inputLength).doubleValue());
        }
    }
}
```

### 2.2 Configuration 등록

```java
// infrastructure/monitoring/config/MonitoringConfiguration.java (수정)
@Configuration
public class MonitoringConfiguration {

    @Bean
    public MicrometerPipelineMetricsReporter micrometerReporter(MeterRegistry meterRegistry) {
        return new MicrometerPipelineMetricsReporter(meterRegistry);
    }

    @Bean
    @Primary
    public PipelineMetricsReporter compositePipelineMetricsReporter(
            PerformanceMetricsRepository performanceMetricsRepository,
            UsageAnalyticsRepository usageAnalyticsRepository,
            MeterRegistry meterRegistry) {

        LoggingPipelineMetricsReporter loggingReporter = new LoggingPipelineMetricsReporter();
        PersistentPipelineMetricsReporter persistentReporter =
            new PersistentPipelineMetricsReporter(
                performanceMetricsRepository,
                usageAnalyticsRepository,
                loggingReporter
            );
        MicrometerPipelineMetricsReporter micrometerReporter =
            new MicrometerPipelineMetricsReporter(meterRegistry);

        return new CompositeMetricsReporter(persistentReporter, micrometerReporter);
    }
}
```

### 2.3 Composite Reporter 구현

```java
// application/monitoring/monitor/CompositeMetricsReporter.java
package com.study.webflux.rag.application.monitoring.monitor;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CompositeMetricsReporter implements PipelineMetricsReporter {

    private final List<PipelineMetricsReporter> reporters;

    public CompositeMetricsReporter(PipelineMetricsReporter... reporters) {
        this.reporters = List.of(reporters);
    }

    @Override
    public void report(DialoguePipelineTracker.PipelineSummary summary) {
        reporters.forEach(reporter -> {
            try {
                reporter.report(summary);
            } catch (Exception e) {
                // 하나의 reporter 실패가 전체 실패로 이어지지 않도록
            }
        });
    }
}
```

---

## Phase 3: Grafana 대시보드 구성

### 3.1 대시보드 구조

총 3개의 대시보드를 구성합니다:

1. **Pipeline Overview** - 전체 파이프라인 모니터링
2. **Stage Performance** - 스테이지별 상세 분석
3. **Cost & Usage** - 비용 및 리소스 사용량

### 3.2 Dashboard 1: Pipeline Overview

#### 패널 구성

**Row 1: Key Metrics (4개 Stat 패널)**

1. Total Requests (지난 1시간)
```promql
sum(increase(dialogue_pipeline_total[1h]))
```

2. Success Rate (지난 1시간)
```promql
sum(increase(dialogue_pipeline_total{status="completed"}[1h]))
/
sum(increase(dialogue_pipeline_total[1h])) * 100
```

3. Average Response Time (지난 5분)
```promql
rate(dialogue_pipeline_duration_sum[5m])
/
rate(dialogue_pipeline_duration_count[5m])
```

4. P95 Response Time (지난 5분)
```promql
histogram_quantile(0.95,
  rate(dialogue_pipeline_duration_bucket[5m])
)
```

**Row 2: Throughput & Latency (2개 Graph 패널)**

1. Request Rate (초당 요청 수)
```promql
sum(rate(dialogue_pipeline_total[1m])) by (status)
```

2. Response Time Percentiles
```promql
# P50
histogram_quantile(0.50, rate(dialogue_pipeline_duration_bucket[5m]))

# P90
histogram_quantile(0.90, rate(dialogue_pipeline_duration_bucket[5m]))

# P95
histogram_quantile(0.95, rate(dialogue_pipeline_duration_bucket[5m]))

# P99
histogram_quantile(0.99, rate(dialogue_pipeline_duration_bucket[5m]))
```

**Row 3: TTFB Analysis (1개 Graph 패널)**

Time to First Byte (첫 응답 지연)
```promql
rate(dialogue_pipeline_first_response_sum[5m])
/
rate(dialogue_pipeline_first_response_count[5m])
```

**Row 4: Error Tracking (2개 패널)**

1. Error Rate (Graph)
```promql
sum(rate(dialogue_pipeline_total{status="failed"}[5m]))
/
sum(rate(dialogue_pipeline_total[5m])) * 100
```

2. Error Count by Stage (Table)
```promql
sum(increase(dialogue_stage_duration_count{status="failed"}[1h])) by (stage)
```

### 3.3 Dashboard 2: Stage Performance

#### 패널 구성

**Row 1: Stage Duration Heatmap**

각 스테이지별 실행 시간 분포
```promql
sum(rate(dialogue_stage_duration_bucket[5m])) by (stage, le)
```
- 시각화: Heatmap
- X축: 시간
- Y축: 스테이지
- 색상: 실행 시간

**Row 2: Stage Duration Breakdown (Stacked Graph)**

```promql
rate(dialogue_stage_duration_sum[1m]) by (stage)
/
rate(dialogue_stage_duration_count[1m]) by (stage)
```

**Row 3: Individual Stage Metrics (8개 Gauge 패널)**

각 스테이지별 평균 실행 시간:
```promql
# INPUT
rate(dialogue_stage_duration_sum{stage="input"}[5m])
/
rate(dialogue_stage_duration_count{stage="input"}[5m])

# MEMORY_RETRIEVAL
rate(dialogue_stage_duration_sum{stage="memory_retrieval"}[5m])
/
rate(dialogue_stage_duration_count{stage="memory_retrieval"}[5m])

# RETRIEVAL
rate(dialogue_stage_duration_sum{stage="retrieval"}[5m])
/
rate(dialogue_stage_duration_count{stage="retrieval"}[5m])

# LLM_COMPLETION
rate(dialogue_stage_duration_sum{stage="llm_completion"}[5m])
/
rate(dialogue_stage_duration_count{stage="llm_completion"}[5m])

# SENTENCE_ASSEMBLY
rate(dialogue_stage_duration_sum{stage="sentence_assembly"}[5m])
/
rate(dialogue_stage_duration_count{stage="sentence_assembly"}[5m])

# TTS_SYNTHESIS
rate(dialogue_stage_duration_sum{stage="tts_synthesis"}[5m])
/
rate(dialogue_stage_duration_count{stage="tts_synthesis"}[5m])

# AUDIO_STREAMING
rate(dialogue_stage_duration_sum{stage="audio_streaming"}[5m])
/
rate(dialogue_stage_duration_count{stage="audio_streaming"}[5m])

# POST_PROCESSING
rate(dialogue_stage_duration_sum{stage="post_processing"}[5m])
/
rate(dialogue_stage_duration_count{stage="post_processing"}[5m])
```

**Row 4: Stage Success Rate (Bar Gauge)**

```promql
sum(increase(dialogue_stage_duration_count{status="completed"}[1h])) by (stage)
/
sum(increase(dialogue_stage_duration_count[1h])) by (stage) * 100
```

### 3.4 Dashboard 3: Cost & Usage

#### 패널 구성

**Row 1: LLM Usage (3개 Graph 패널)**

1. Token Consumption Rate (시간당)
```promql
sum(increase(llm_tokens_total[1h])) by (model)
```

2. Token Type Distribution
```promql
# Prompt Tokens
sum(rate(llm_tokens_prompt[5m])) by (model)

# Completion Tokens
sum(rate(llm_tokens_completion[5m])) by (model)
```

3. Average Tokens per Request
```promql
rate(llm_tokens_total_sum[5m])
/
rate(dialogue_pipeline_total_count[5m])
```

**Row 2: Retrieval Metrics (2개 Graph 패널)**

1. Memory Retrieval Count
```promql
rate(retrieval_memory_count[5m])
```

2. Document Retrieval Count
```promql
rate(retrieval_document_count[5m])
```

**Row 3: TTS Metrics (3개 패널)**

1. Audio Chunks Generated (Counter)
```promql
sum(increase(tts_audio_chunks[1h]))
```

2. Average Audio Length
```promql
rate(tts_audio_length_sum[5m])
/
rate(tts_audio_length_count[5m])
```

3. TTS Synthesis Rate (Graph)
```promql
rate(tts_audio_chunks[1m])
```

**Row 4: Input Analysis (2개 패널)**

1. Average Input Length
```promql
rate(dialogue_input_length_sum[5m])
/
rate(dialogue_input_length_count[5m])
```

2. Input Length Distribution (Histogram)
```promql
sum(rate(dialogue_input_length_bucket[5m])) by (le)
```

**Row 5: Sentence Generation (1개 Graph 패널)**

```promql
rate(llm_sentence_count_sum[5m])
/
rate(llm_sentence_count_count[5m])
```

### 3.5 Grafana Provisioning 설정

#### Datasource Provisioning

```yaml
# monitoring/grafana/provisioning/datasources/prometheus.yml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

#### Dashboard Provisioning

```yaml
# monitoring/grafana/provisioning/dashboards/dashboards.yml
apiVersion: 1

providers:
  - name: 'RAG Pipeline'
    orgId: 1
    folder: 'RAG Monitoring'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
```

---

## Phase 4: 알림 설정

### 4.1 Alertmanager 설정

```yaml
# monitoring/alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'slack-notifications'

receivers:
  - name: 'slack-notifications'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#rag-alerts'
        title: 'RAG Pipeline Alert'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

### 4.2 Prometheus Alert Rules

```yaml
# monitoring/alert-rules.yml
groups:
  - name: rag_pipeline_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: |
          sum(rate(dialogue_pipeline_total{status="failed"}[5m]))
          /
          sum(rate(dialogue_pipeline_total[5m])) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }} (threshold: 5%)"

      - alert: SlowResponseTime
        expr: |
          histogram_quantile(0.95,
            rate(dialogue_pipeline_duration_bucket[5m])
          ) > 5000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 response time is high"
          description: "P95 latency is {{ $value }}ms (threshold: 5000ms)"

      - alert: LLMTokensSpike
        expr: |
          rate(llm_tokens_total[5m]) > 10000
        for: 2m
        labels:
          severity: info
        annotations:
          summary: "LLM token usage spike detected"
          description: "Token consumption rate: {{ $value }} tokens/sec"

      - alert: TTSSynthesisFailed
        expr: |
          sum(increase(dialogue_stage_duration_count{stage="tts_synthesis",status="failed"}[5m])) > 5
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Multiple TTS synthesis failures"
          description: "{{ $value }} TTS failures in the last 5 minutes"

      - alert: MemoryRetrievalSlow
        expr: |
          rate(dialogue_stage_duration_sum{stage="memory_retrieval"}[5m])
          /
          rate(dialogue_stage_duration_count{stage="memory_retrieval"}[5m]) > 500
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Memory retrieval is slow"
          description: "Average duration: {{ $value }}ms (threshold: 500ms)"
```

### 4.3 Prometheus 설정 업데이트

```yaml
# monitoring/prometheus.yml (업데이트)
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - "/etc/prometheus/alert-rules.yml"

scrape_configs:
  - job_name: 'spring-boot-rag'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
        labels:
          application: 'webflux-dialogue'
          environment: 'local'
```

---

## Phase 5: 비용 메트릭 추가 (선택 사항)

### 5.1 비용 계산 메트릭 추가

```java
// MicrometerPipelineMetricsReporter.java에 추가
private void recordCostMetrics(DialoguePipelineTracker.PipelineSummary summary) {
    summary.stages().stream()
        .filter(stage -> stage.stage() == DialoguePipelineStage.LLM_COMPLETION)
        .forEach(stage -> {
            Object tokens = stage.attributes().get("totalTokens");
            String model = String.valueOf(stage.attributes().getOrDefault("model", "unknown"));

            if (tokens instanceof Number) {
                double cost = calculateCost(model, ((Number) tokens).intValue());

                Counter.builder("llm.cost.usd")
                    .tag("model", model)
                    .description("Estimated LLM cost in USD")
                    .register(meterRegistry)
                    .increment(cost);
            }
        });
}

private double calculateCost(String model, int tokens) {
    // GPT-4 Turbo: $0.01 per 1K prompt tokens, $0.03 per 1K completion tokens
    // 간단히 평균 $0.02 per 1K tokens로 계산
    return (tokens / 1000.0) * 0.02;
}
```

### 5.2 Grafana 비용 패널

```promql
# 시간당 예상 비용
sum(increase(llm_cost_usd[1h])) by (model)

# 일일 예상 비용
sum(increase(llm_cost_usd[24h]))

# 요청당 평균 비용
rate(llm_cost_usd_sum[5m])
/
rate(dialogue_pipeline_total_count[5m])
```

---

## 실행 및 검증

### 1. 모니터링 스택 시작

```bash
# Prometheus, Grafana 시작
docker-compose up -d prometheus grafana

# 로그 확인
docker-compose logs -f prometheus grafana
```

### 2. 접속 확인

- **Prometheus UI**: http://localhost:9090
  - Targets 확인: http://localhost:9090/targets
  - 메트릭 쿼리: `dialogue_pipeline_total`

- **Grafana UI**: http://localhost:3000
  - ID: admin / PW: admin
  - Datasource 확인: Configuration > Data Sources > Prometheus
  - 대시보드 Import: Dashboards > Import

### 3. 메트릭 생성 테스트

```bash
# API 호출로 메트릭 생성
curl -X POST http://localhost:8081/api/v1/dialogue \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "sessionId": "test-session",
    "message": "안녕하세요, 테스트 메시지입니다.",
    "voice": "WOMAN_NORMAL",
    "audioFormat": "WAV"
  }'

# Prometheus에서 메트릭 확인
curl http://localhost:9090/api/v1/query?query=dialogue_pipeline_total
```

### 4. 대시보드 확인

1. Grafana 접속 후 Explore 메뉴 선택
2. 쿼리 실행:
   - `dialogue_pipeline_total`
   - `dialogue_stage_duration_count`
   - `llm_tokens_total`
3. 그래프에 데이터 표시 확인

---

## 성능 고려사항

### 1. Cardinality 관리

- **높은 Cardinality 회피**: `pipelineId`는 태그로 사용하지 않음 (무한 증가)
- **적절한 태그 선택**: status, stage, model 등 고정된 값만 사용

### 2. 메트릭 보존 기간

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

storage:
  tsdb:
    retention.time: 15d  # 15일간 보존
    retention.size: 10GB # 최대 10GB
```

### 3. Aggregation 최적화

- Recording Rules 사용으로 자주 쿼리하는 집계 미리 계산

```yaml
# recording-rules.yml
groups:
  - name: rag_recording_rules
    interval: 1m
    rules:
      - record: job:dialogue_pipeline_duration:avg
        expr: |
          rate(dialogue_pipeline_duration_sum[5m])
          /
          rate(dialogue_pipeline_duration_count[5m])

      - record: job:dialogue_pipeline:success_rate
        expr: |
          sum(rate(dialogue_pipeline_total{status="completed"}[5m]))
          /
          sum(rate(dialogue_pipeline_total[5m]))
```

---

## 예상 타임라인

| Phase | 작업 | 예상 시간 |
|-------|-----|---------|
| Phase 1 | 의존성 추가 및 Docker 설정 | 1시간 |
| Phase 2 | Micrometer 구현 및 테스트 | 3시간 |
| Phase 3 | Grafana 대시보드 3개 구성 | 4시간 |
| Phase 4 | 알림 설정 및 테스트 | 2시간 |
| Phase 5 | 비용 메트릭 추가 (선택) | 1시간 |
| **총계** | | **11시간** |

---

## 체크리스트

### 구현 전
- [ ] Micrometer 의존성 추가
- [ ] Docker Compose 업데이트
- [ ] Prometheus 설정 파일 작성
- [ ] Grafana provisioning 설정

### 구현 중
- [ ] MicrometerPipelineMetricsReporter 작성
- [ ] CompositeMetricsReporter 작성
- [ ] Configuration 등록
- [ ] 로컬 테스트로 메트릭 확인

### 대시보드 구성
- [ ] Pipeline Overview 대시보드 작성
- [ ] Stage Performance 대시보드 작성
- [ ] Cost & Usage 대시보드 작성
- [ ] 대시보드 JSON export 및 버전 관리

### 알림 설정
- [ ] Alert Rules 작성
- [ ] Alertmanager 설정
- [ ] Slack Webhook 연동
- [ ] 테스트 알림 발송

### 검증
- [ ] Prometheus Targets 상태 확인
- [ ] Grafana 대시보드 데이터 표시 확인
- [ ] 알림 수신 테스트
- [ ] 부하 테스트로 메트릭 검증

---

## 참고 자료

- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Prometheus Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)

---

## 트러블슈팅

### 문제 1: Prometheus가 메트릭을 수집하지 못함

**증상**: Targets 페이지에서 `DOWN` 상태

**해결**:
```bash
# Docker 내부에서 호스트 접근 확인
docker exec -it prometheus ping host.docker.internal

# Mac/Windows: host.docker.internal 사용
# Linux: host.docker.internal 대신 host IP 직접 사용
```

### 문제 2: Grafana에서 "No data" 표시

**원인**: PromQL 쿼리 오류 또는 메트릭 미생성

**해결**:
1. Prometheus UI에서 동일 쿼리 실행
2. Actuator endpoint 직접 확인: `http://localhost:8081/actuator/prometheus`
3. 메트릭 이름 정확히 확인 (underscore vs dot)

### 문제 3: High Cardinality 경고

**증상**: Prometheus 메모리 사용량 급증

**해결**:
- `pipelineId`, `userId` 같은 높은 cardinality 태그 제거
- Recording Rules로 집계 미리 계산
- Retention 기간 단축
