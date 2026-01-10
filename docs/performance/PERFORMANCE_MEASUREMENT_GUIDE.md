# 성능 지표 측정 및 증거 수집 가이드

## 목차
1. [JMH 벤치마크 테스트](#1-jmh-벤치마크-테스트)
2. [k6 부하 테스트](#2-k6-부하-테스트)
3. [Spring Actuator + Prometheus + Grafana](#3-spring-actuator--prometheus--grafana)
4. [증거 수집 및 문서화](#4-증거-수집-및-문서화)

---

## 1. JMH 벤치마크 테스트

### 1.1 의존성 추가

**build.gradle**:
```groovy
plugins {
    id 'me.champeau.jmh' version '0.7.2'
}

dependencies {
    jmh 'org.openjdk.jmh:jmh-core:1.37'
    jmh 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
}

jmh {
    iterations = 5
    warmupIterations = 3
    fork = 2
    resultFormat = 'JSON'
    resultsFile = file("${buildDir}/reports/jmh/results.json")
    humanOutputFile = file("${buildDir}/reports/jmh/human.txt")
}
```

### 1.2 벤치마크 클래스 작성

**src/jmh/java/com/study/webflux/rag/benchmark/**

#### TtsLoadBalancerBenchmark.java
```java
package com.study.webflux.rag.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TtsLoadBalancerBenchmark {

    private TtsLoadBalancer loadBalancer;
    private TtsLoadBalancer naiveLoadBalancer;

    @Setup
    public void setup() {
        loadBalancer = createOptimizedLoadBalancer();
        naiveLoadBalancer = createNaiveLoadBalancer();
    }

    @Benchmark
    public void optimizedSelectEndpoint(Blackhole bh) {
        bh.consume(loadBalancer.selectEndpoint());
    }

    @Benchmark
    public void naiveSelectEndpoint(Blackhole bh) {
        bh.consume(naiveLoadBalancer.selectEndpoint());
    }

    @Benchmark
    @Threads(50)
    public void optimizedConcurrentSelect(Blackhole bh) {
        bh.consume(loadBalancer.selectEndpoint());
    }

    @Benchmark
    @Threads(50)
    public void naiveConcurrentSelect(Blackhole bh) {
        bh.consume(naiveLoadBalancer.selectEndpoint());
    }
}
```

#### ParallelInputPreparationBenchmark.java
```java
package com.study.webflux.rag.benchmark;

import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Mono;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ParallelInputPreparationBenchmark {

    private DialogueInputService parallelService;
    private SequentialDialogueInputService sequentialService;

    @Setup
    public void setup() {
        parallelService = createParallelService();
        sequentialService = createSequentialService();
    }

    @Benchmark
    public PipelineInputs parallelPrepareInputs() {
        return parallelService.prepareInputs("테스트 쿼리입니다.").block();
    }

    @Benchmark
    public PipelineInputs sequentialPrepareInputs() {
        return sequentialService.prepareInputs("테스트 쿼리입니다.").block();
    }
}
```

#### MongoQueryBenchmark.java
```java
package com.study.webflux.rag.benchmark;

import org.openjdk.jmh.annotations.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MongoQueryBenchmark {

    private UsageAnalyticsRepository repository;
    private Instant start;
    private Instant end;

    @Setup
    public void setup() {
        repository = createRepository();
        end = Instant.now();
        start = end.minus(1, ChronoUnit.HOURS);
    }

    @Benchmark
    public long queryWithIndex() {
        return repository.findByTimestampBetweenAndModel(start, end, "gpt-4")
            .count()
            .block();
    }

    @Benchmark
    public long queryWithoutIndex() {
        return repository.findByTimestampBetweenAndModelNoIndex(start, end, "gpt-4")
            .count()
            .block();
    }
}
```

### 1.3 실행 및 결과 수집

```bash
./gradlew jmh

cat build/reports/jmh/results.json
```

**결과 예시 (results.json)**:
```json
[
  {
    "benchmark": "TtsLoadBalancerBenchmark.optimizedSelectEndpoint",
    "mode": "thrpt",
    "threads": 1,
    "forks": 2,
    "warmupIterations": 3,
    "measurementIterations": 5,
    "primaryMetric": {
      "score": 15234.567,
      "scoreError": 123.456,
      "scoreUnit": "ops/ms"
    }
  },
  {
    "benchmark": "TtsLoadBalancerBenchmark.naiveSelectEndpoint",
    "mode": "thrpt",
    "threads": 1,
    "forks": 2,
    "primaryMetric": {
      "score": 8123.456,
      "scoreError": 234.567,
      "scoreUnit": "ops/ms"
    }
  }
]
```

### 1.4 시각화 (JMH Visualizer)

**jmh-result-visualizer.html** 생성:
```bash
./gradlew jmh
open https://jmh.morethan.io/
```
→ results.json 업로드 → 차트 스크린샷 저장

---

## 2. k6 부하 테스트

### 2.1 k6 설치

```bash
brew install k6
```

### 2.2 테스트 스크립트 작성

**load-tests/dialogue-pipeline-test.js**:
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const latency = new Trend('latency');

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      stages: [
        { duration: '1m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 50 },
      ],
      preAllocatedVUs: 300,
      maxVUs: 500,
      startTime: '3m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export default function () {
  const payload = JSON.stringify({
    text: '오늘 날씨가 어때요?',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '10s',
  };

  const startTime = Date.now();
  const res = http.post(`${BASE_URL}/rag/dialogue/text`, payload, params);
  const duration = Date.now() - startTime;

  latency.add(duration);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response has content': (r) => r.body.length > 0,
    'latency < 1000ms': () => duration < 1000,
  });

  errorRate.add(!success);
  sleep(0.1);
}

export function handleSummary(data) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

  return {
    [`reports/k6-summary-${timestamp}.json`]: JSON.stringify(data, null, 2),
    [`reports/k6-summary-${timestamp}.html`]: generateHtmlReport(data),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function generateHtmlReport(data) {
  return `
<!DOCTYPE html>
<html>
<head>
  <title>k6 Load Test Report</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 40px; }
    .metric { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 8px; }
    .metric-name { font-weight: bold; color: #333; }
    .metric-value { font-size: 24px; color: #2196F3; }
    .threshold-pass { color: green; }
    .threshold-fail { color: red; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
    th { background-color: #4CAF50; color: white; }
  </style>
</head>
<body>
  <h1>k6 Load Test Report</h1>
  <p>Generated: ${new Date().toISOString()}</p>

  <h2>Summary</h2>
  <table>
    <tr><th>Metric</th><th>Value</th></tr>
    <tr><td>Total Requests</td><td>${data.metrics.http_reqs.values.count}</td></tr>
    <tr><td>Failed Requests</td><td>${data.metrics.http_req_failed?.values?.passes || 0}</td></tr>
    <tr><td>Avg Latency</td><td>${data.metrics.http_req_duration.values.avg.toFixed(2)}ms</td></tr>
    <tr><td>P95 Latency</td><td>${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms</td></tr>
    <tr><td>P99 Latency</td><td>${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms</td></tr>
    <tr><td>Throughput</td><td>${(data.metrics.http_reqs.values.rate).toFixed(2)} req/s</td></tr>
  </table>

  <h2>Thresholds</h2>
  <table>
    <tr><th>Threshold</th><th>Result</th></tr>
    ${Object.entries(data.thresholds || {}).map(([name, result]) => `
      <tr>
        <td>${name}</td>
        <td class="${result.ok ? 'threshold-pass' : 'threshold-fail'}">${result.ok ? 'PASS' : 'FAIL'}</td>
      </tr>
    `).join('')}
  </table>
</body>
</html>
  `;
}
```

**load-tests/parallel-vs-sequential-test.js**:
```javascript
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const parallelLatency = new Trend('parallel_latency');
const sequentialLatency = new Trend('sequential_latency');

export const options = {
  scenarios: {
    parallel: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 100,
      exec: 'parallelTest',
      env: { MODE: 'parallel' },
    },
    sequential: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 100,
      exec: 'sequentialTest',
      startTime: '2m',
      env: { MODE: 'sequential' },
    },
  },
};

export function parallelTest() {
  const start = Date.now();
  const res = http.post('http://localhost:8081/rag/dialogue/text?mode=parallel',
    JSON.stringify({ text: '테스트' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  parallelLatency.add(Date.now() - start);
  check(res, { 'parallel success': (r) => r.status === 200 });
}

export function sequentialTest() {
  const start = Date.now();
  const res = http.post('http://localhost:8081/rag/dialogue/text?mode=sequential',
    JSON.stringify({ text: '테스트' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  sequentialLatency.add(Date.now() - start);
  check(res, { 'sequential success': (r) => r.status === 200 });
}
```

### 2.3 실행 및 결과 수집

```bash
mkdir -p reports

k6 run load-tests/dialogue-pipeline-test.js \
  --out json=reports/k6-metrics.json

k6 run load-tests/parallel-vs-sequential-test.js
```

### 2.4 k6 Cloud로 시각화 (선택사항)

```bash
k6 cloud load-tests/dialogue-pipeline-test.js
```
→ 대시보드 링크 + 스크린샷 저장

---

## 3. Spring Actuator + Prometheus + Grafana

### 3.1 의존성 추가

**build.gradle**:
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 3.2 설정

**application.yml**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: webflux-dialogue
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      slo:
        http.server.requests: 100ms, 500ms, 1000ms
```

### 3.3 커스텀 메트릭 추가

**PipelineMetrics.java**:
```java
package com.study.webflux.rag.application.monitoring.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PipelineMetrics {

    private final MeterRegistry registry;
    private final Timer inputPreparationTimer;
    private final Timer llmStreamTimer;
    private final Timer ttsStreamTimer;
    private final Counter pipelineSuccessCounter;
    private final Counter pipelineErrorCounter;
    private final AtomicInteger activePipelines;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.inputPreparationTimer = Timer.builder("pipeline.stage.duration")
            .tag("stage", "input_preparation")
            .description("Input preparation stage duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.llmStreamTimer = Timer.builder("pipeline.stage.duration")
            .tag("stage", "llm_stream")
            .description("LLM streaming stage duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.ttsStreamTimer = Timer.builder("pipeline.stage.duration")
            .tag("stage", "tts_stream")
            .description("TTS streaming stage duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.pipelineSuccessCounter = Counter.builder("pipeline.completed")
            .tag("status", "success")
            .register(registry);

        this.pipelineErrorCounter = Counter.builder("pipeline.completed")
            .tag("status", "error")
            .register(registry);

        this.activePipelines = registry.gauge("pipeline.active", new AtomicInteger(0));
    }

    public Timer.Sample startInputPreparation() {
        activePipelines.incrementAndGet();
        return Timer.start(registry);
    }

    public void recordInputPreparation(Timer.Sample sample) {
        sample.stop(inputPreparationTimer);
    }

    public void recordLlmStream(Duration duration) {
        llmStreamTimer.record(duration);
    }

    public void recordTtsStream(Duration duration) {
        ttsStreamTimer.record(duration);
    }

    public void recordSuccess() {
        activePipelines.decrementAndGet();
        pipelineSuccessCounter.increment();
    }

    public void recordError() {
        activePipelines.decrementAndGet();
        pipelineErrorCounter.increment();
    }
}
```

**TtsLoadBalancerMetrics.java**:
```java
@Component
public class TtsLoadBalancerMetrics {

    private final Counter endpointSelectionCounter;
    private final Counter failoverCounter;
    private final Gauge healthyEndpointsGauge;

    public TtsLoadBalancerMetrics(MeterRegistry registry, TtsLoadBalancer loadBalancer) {
        this.endpointSelectionCounter = Counter.builder("tts.endpoint.selection")
            .description("TTS endpoint selection count")
            .register(registry);

        this.failoverCounter = Counter.builder("tts.failover")
            .description("TTS failover count")
            .register(registry);

        Gauge.builder("tts.endpoints.healthy", loadBalancer, TtsLoadBalancer::getHealthyEndpointCount)
            .description("Number of healthy TTS endpoints")
            .register(registry);

        Gauge.builder("tts.endpoints.total", loadBalancer, lb -> lb.getEndpoints().size())
            .description("Total number of TTS endpoints")
            .register(registry);
    }

    public void recordSelection(String endpointId) {
        endpointSelectionCounter.increment();
    }

    public void recordFailover() {
        failoverCounter.increment();
    }
}
```

### 3.4 Docker Compose (Prometheus + Grafana)

**docker-compose.monitoring.yml**:
```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:v2.48.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'

  grafana:
    image: grafana/grafana:10.2.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
      - ./grafana/dashboards:/var/lib/grafana/dashboards
      - grafana-data:/var/lib/grafana

volumes:
  prometheus-data:
  grafana-data:
```

**prometheus/prometheus.yml**:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'webflux-dialogue'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
    scrape_interval: 5s
```

### 3.5 Grafana 대시보드

**grafana/dashboards/pipeline-performance.json**:
```json
{
  "dashboard": {
    "title": "RAG Pipeline Performance",
    "panels": [
      {
        "title": "Pipeline Throughput",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(pipeline_completed_total[1m])",
            "legendFormat": "{{status}}"
          }
        ]
      },
      {
        "title": "Stage Duration P99",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(pipeline_stage_duration_seconds_bucket[5m]))",
            "legendFormat": "{{stage}}"
          }
        ]
      },
      {
        "title": "Input Preparation: Parallel vs Sequential",
        "type": "stat",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(pipeline_stage_duration_seconds_bucket{stage='input_preparation'}[5m]))"
          }
        ]
      },
      {
        "title": "TTS Endpoint Health",
        "type": "gauge",
        "targets": [
          {
            "expr": "tts_endpoints_healthy / tts_endpoints_total * 100"
          }
        ]
      },
      {
        "title": "TTS Failover Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(tts_failover_total[5m])"
          }
        ]
      },
      {
        "title": "MongoDB Query Duration",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(mongodb_driver_commands_seconds_bucket{command='find'}[5m]))"
          }
        ]
      }
    ]
  }
}
```

### 3.6 실행

```bash
docker-compose -f docker-compose.monitoring.yml up -d

./gradlew :webflux-dialogue:bootRun

open http://localhost:3000
```

---

## 4. 증거 수집 및 문서화

### 4.1 증거 수집 체크리스트

| 카테고리 | 증거 유형 | 저장 위치 | 형식 |
|---------|----------|----------|------|
| JMH 벤치마크 | 결과 JSON | `reports/jmh/` | JSON |
| JMH 벤치마크 | 시각화 차트 | `docs/evidence/jmh/` | PNG |
| k6 부하테스트 | 요약 리포트 | `reports/k6/` | HTML, JSON |
| k6 부하테스트 | 처리량 그래프 | `docs/evidence/k6/` | PNG |
| Grafana | 대시보드 스크린샷 | `docs/evidence/grafana/` | PNG |
| Grafana | 대시보드 JSON | `grafana/dashboards/` | JSON |
| MongoDB | Explain Plan 결과 | `docs/evidence/mongodb/` | JSON |
| Git | 최적화 전/후 커밋 | Git History | Diff |

### 4.2 증거 수집 스크립트

**scripts/collect-evidence.sh**:
```bash
#!/bin/bash

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
EVIDENCE_DIR="docs/evidence/${TIMESTAMP}"

mkdir -p ${EVIDENCE_DIR}/{jmh,k6,grafana,mongodb}

echo "=== 1. JMH 벤치마크 실행 ==="
./gradlew jmh
cp build/reports/jmh/results.json ${EVIDENCE_DIR}/jmh/

echo "=== 2. k6 부하 테스트 실행 ==="
k6 run load-tests/dialogue-pipeline-test.js \
  --out json=${EVIDENCE_DIR}/k6/metrics.json \
  2>&1 | tee ${EVIDENCE_DIR}/k6/output.txt

echo "=== 3. MongoDB Explain Plan 수집 ==="
mongosh --quiet ragdb --eval '
  var result = db.usage_analytics.find({
    timestamp: {$gte: new Date(Date.now() - 3600000)},
    "llmUsage.model": "gpt-4"
  }).explain("executionStats");
  printjson(result);
' > ${EVIDENCE_DIR}/mongodb/explain-with-index.json

echo "=== 4. Prometheus 메트릭 스냅샷 ==="
curl -s http://localhost:9090/api/v1/query?query=pipeline_stage_duration_seconds \
  > ${EVIDENCE_DIR}/prometheus-metrics.json

echo "=== 5. 증거 요약 생성 ==="
cat > ${EVIDENCE_DIR}/SUMMARY.md << EOF
# Performance Evidence Summary
Generated: $(date)

## JMH Benchmark Results
$(cat ${EVIDENCE_DIR}/jmh/results.json | jq -r '.[] | "- \(.benchmark): \(.primaryMetric.score) \(.primaryMetric.scoreUnit)"')

## k6 Load Test Results
$(cat ${EVIDENCE_DIR}/k6/metrics.json | jq -r 'select(.type=="Point") | "\(.metric): \(.data.value)"' | head -20)

## MongoDB Query Performance
- With Index: $(cat ${EVIDENCE_DIR}/mongodb/explain-with-index.json | jq '.executionStats.executionTimeMillis')ms
EOF

echo "=== 증거 수집 완료: ${EVIDENCE_DIR} ==="
```

### 4.3 Before/After 비교 문서화

**docs/evidence/COMPARISON.md**:
```markdown
# 성능 최적화 Before/After 비교

## 1. TTS 로드 밸런서

### Before (단일 엔드포인트)
- 가용성: 99.5%
- 장애 복구: 수동 (30-60초)
- 부하 분산: 없음

**증거**: `evidence/before/tts-single-endpoint.json`

### After (Health-aware 로드 밸런서)
- 가용성: 99.99%
- 장애 복구: 자동 (즉시)
- 부하 분산: ±5% 편차

**증거**: `evidence/after/tts-load-balancer.json`

**Git Commit**: abc123 - "feat: implement health-aware TTS load balancer"

---

## 2. 입력 준비 병렬화

### Before (순차 실행)
- P50: 180ms
- P99: 250ms
- 처리량: 70 req/s

**증거**: `evidence/before/sequential-input-preparation.png`

### After (병렬 실행)
- P50: 100ms
- P99: 130ms
- 처리량: 126 req/s

**증거**: `evidence/after/parallel-input-preparation.png`

**개선율**: P50 44.4% 감소, 처리량 80% 향상

**Git Commit**: def456 - "perf: parallelize input preparation with Mono.zip"

---

## 3. MongoDB 쿼리 인덱스

### Before (Collection Scan)
```
executionTimeMillis: 5000
totalDocsExamined: 1000000
stage: COLLSCAN
```

**증거**: `evidence/before/mongodb-no-index.json`

### After (Index Scan)
```
executionTimeMillis: 50
totalDocsExamined: 1000
stage: IXSCAN
```

**증거**: `evidence/after/mongodb-with-index.json`

**개선율**: 100배 향상

**Git Commit**: ghi789 - "perf: add compound index for usage analytics"
```

### 4.4 포트폴리오용 증거 패키지

**scripts/create-portfolio-evidence.sh**:
```bash
#!/bin/bash

PORTFOLIO_DIR="portfolio-evidence"
mkdir -p ${PORTFOLIO_DIR}

echo "=== 포트폴리오 증거 패키지 생성 ==="

cp docs/evidence/COMPARISON.md ${PORTFOLIO_DIR}/
cp docs/evidence/*/jmh/*.png ${PORTFOLIO_DIR}/
cp docs/evidence/*/k6/*.html ${PORTFOLIO_DIR}/
cp docs/evidence/*/grafana/*.png ${PORTFOLIO_DIR}/

cat > ${PORTFOLIO_DIR}/README.md << 'EOF'
# 성능 최적화 증거 패키지

## 파일 목록

### JMH 벤치마크 결과
- `jmh-tts-loadbalancer.png` - 로드 밸런서 처리량 비교
- `jmh-parallel-input.png` - 병렬 vs 순차 입력 준비

### k6 부하 테스트 리포트
- `k6-baseline.html` - 기준 부하 테스트 결과
- `k6-stress.html` - 스트레스 테스트 결과

### Grafana 대시보드
- `grafana-pipeline-overview.png` - 파이프라인 전체 성능
- `grafana-tts-health.png` - TTS 엔드포인트 상태
- `grafana-mongodb-query.png` - MongoDB 쿼리 성능

### 비교 문서
- `COMPARISON.md` - Before/After 상세 비교

## 재현 방법
1. `./gradlew jmh` - JMH 벤치마크 실행
2. `k6 run load-tests/*.js` - k6 부하 테스트 실행
3. `docker-compose -f docker-compose.monitoring.yml up` - 모니터링 시작
EOF

zip -r portfolio-evidence.zip ${PORTFOLIO_DIR}

echo "=== 완료: portfolio-evidence.zip ==="
```

---

## 5. 증거 제시 방식

### 5.1 이력서/포트폴리오에서 제시

```markdown
## 성능 최적화 프로젝트

### 정량적 성과 (측정 기반)
- 처리량: 100 → 200 req/s (JMH 벤치마크 측정)
- P99 레이턴시: 2000ms → 800ms (k6 부하테스트)
- MongoDB 쿼리: 5000ms → 50ms (Explain Plan 분석)

📎 증거 자료: [GitHub - /docs/evidence/](링크)
```

### 5.2 면접에서 제시

1. **노트북에 Grafana 대시보드 준비**
   - 실시간 메트릭 시연 가능

2. **JMH 결과 JSON + 시각화**
   - 재현 가능한 벤치마크 코드 설명

3. **Git History**
   - Before/After 커밋 diff 비교

4. **k6 HTML 리포트**
   - 부하 테스트 시나리오 설명

### 5.3 GitHub README에 배지 추가

```markdown
![Performance](https://img.shields.io/badge/throughput-200%20req%2Fs-brightgreen)
![Latency](https://img.shields.io/badge/P99%20latency-800ms-blue)
![Availability](https://img.shields.io/badge/availability-99.99%25-green)
```

---

## 요약: 증거 수집 우선순위

| 우선순위 | 방법 | 난이도 | 신뢰도 | 재현 가능성 |
|---------|------|-------|-------|-----------|
| 1 | JMH 벤치마크 | 중간 | 높음 | 높음 |
| 2 | MongoDB Explain Plan | 낮음 | 높음 | 높음 |
| 3 | k6 부하 테스트 | 중간 | 높음 | 중간 |
| 4 | Grafana 스크린샷 | 낮음 | 중간 | 낮음 |
| 5 | Git Commit Diff | 낮음 | 높음 | 높음 |

**권장 조합**: JMH + k6 + MongoDB Explain + Git Diff
