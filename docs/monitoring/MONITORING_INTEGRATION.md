# 외부 모니터링 서비스 통합 가이드

## 개요

현재 프로젝트는 RAG 파이프라인의 성능, 비용, 사용량을 추적하는 커스텀 모니터링 시스템을 운영 중입니다.
이 문서는 수집된 메트릭을 Sentry, Grafana, DataDog 등 외부 모니터링 서비스로 전송하는 통합 방안을 설명합니다.

## 현재 수집 중인 메트릭

### 1. 파이프라인 레벨 메트릭
- 전체 응답 시간 (total duration)
- 첫 응답 지연 (time to first byte)
- 마지막 응답 지연 (time to last byte)
- 파이프라인 상태 (COMPLETED, FAILED, CANCELLED)

### 2. 스테이지 레벨 메트릭 (8개 단계)
| 스테이지 | 측정 항목 |
|---------|----------|
| INPUT | 입력 텍스트 길이 |
| MEMORY_RETRIEVAL | 메모리 검색 건수, 수행 시간 |
| RETRIEVAL | 문서 검색 건수, 수행 시간 |
| LLM_COMPLETION | 토큰 사용량 (prompt/completion/total), 모델명, 수행 시간 |
| SENTENCE_ASSEMBLY | 생성 문장 수 |
| TTS_SYNTHESIS | 오디오 청크 수, 오디오 길이, 수행 시간 |
| AUDIO_STREAMING | 스트리밍 이벤트 수 |
| POST_PROCESSING | 처리 시간 |

### 3. 비즈니스 메트릭
- LLM 토큰 사용량 (모델별)
- API 호출 비용 (LLM, TTS)
- 생성된 문장 샘플 (최대 20개)
- 사용자 입력 텍스트 미리보기

### 데이터 저장소
- **MongoDB**: 원시 메트릭 및 집계 데이터 (PerformanceMetrics, UsageAnalytics, Rollups)
- **In-Memory**: 실시간 파이프라인 추적 (DialoguePipelineTracker)

## 통합 가능 범위 비교

### 1. Grafana + Prometheus (권장)

#### 통합 가능 범위: 100%

| 메트릭 유형 | 통합 여부 | Grafana 시각화 |
|------------|---------|---------------|
| 파이프라인 응답시간 | ✅ | Time series (히스토그램) |
| 스테이지별 duration | ✅ | Heatmap, Gauge panels |
| LLM 토큰 사용량 | ✅ | Counter, Rate |
| 비용 트래킹 | ✅ | Stat panels |
| 오류율 | ✅ | Graph + Alert rules |
| 처리량(Throughput) | ✅ | Rate over time |
| 문장 생성 속도 | ✅ | Gauge |
| 메모리/문서 검색 건수 | ✅ | Counter |

#### 통합 방법
- Spring Boot Actuator + Micrometer
- Prometheus Exporter로 메트릭 노출 (`/actuator/prometheus`)
- Grafana 대시보드 구성

#### 구현 예시
```java
// Micrometer Timer로 스테이지별 duration 측정
Timer.Sample sample = Timer.start(meterRegistry);
// ... 스테이지 실행 ...
sample.stop(Timer.builder("dialogue.stage.duration")
    .tag("stage", "LLM_COMPLETION")
    .tag("status", "success")
    .register(meterRegistry));

// LLM 토큰 카운터
Counter.builder("llm.tokens")
    .tag("type", "completion")
    .tag("model", "gpt-4")
    .register(meterRegistry)
    .increment(completionTokens);

// 비용 게이지
Gauge.builder("dialogue.cost.usd", () -> currentCost)
    .tag("service", "openai")
    .register(meterRegistry);
```

#### 장점
- 오픈소스 (무료)
- 실시간 대시보드 구성 가능
- 커스텀 메트릭 100% 지원
- 알림 설정 유연 (Alertmanager 연동)
- MongoDB/Redis 메트릭과 함께 통합 뷰 제공

#### 제약사항
- 직접 인프라 관리 필요 (Prometheus 서버, Grafana 서버)
- 분산 추적(Distributed Tracing)은 Tempo 추가 구성 필요

---

### 2. Sentry

#### 통합 가능 범위: 60%

| 메트릭 유형 | 통합 여부 | Sentry 기능 |
|------------|---------|-----------|
| 파이프라인 실패 | ✅ | Error tracking (context 포함) |
| 스테이지별 duration | ✅ | Performance Transactions |
| LLM 토큰 사용량 | ⚠️ | Custom tags로 전송 가능 (집계 제한) |
| 비용 트래킹 | ❌ | 비즈니스 메트릭 부적합 |
| 오류 스택트레이스 | ✅ | 에러 발생 시점 컨텍스트 캡처 |
| 사용자 세션 | ✅ | Breadcrumbs로 파이프라인 흐름 추적 |
| 느린 트랜잭션 | ✅ | Threshold 기반 알림 |

#### 통합 방법
- Sentry Java SDK (`sentry-spring-boot-starter-jakarta`)
- 에러 트래킹 + 성능 트랜잭션 전송

#### 구현 예시
```java
// 파이프라인을 Sentry Transaction으로 기록
ITransaction transaction = Sentry.startTransaction("dialogue.pipeline", "process");
transaction.setTag("pipelineId", pipelineId);
transaction.setData("inputLength", inputLength);

Span llmSpan = transaction.startChild("llm.completion");
llmSpan.setData("tokens", totalTokens);
llmSpan.setData("model", "gpt-4");
llmSpan.finish();

if (error) {
    transaction.setStatus(SpanStatus.INTERNAL_ERROR);
    Sentry.captureException(error, scope -> {
        scope.setContexts("pipeline", Map.of(
            "stage", "LLM_COMPLETION",
            "tokens", totalTokens
        ));
    });
}
transaction.finish();
```

#### 장점
- 에러 컨텍스트 자동 수집 (스택트레이스, 환경 변수)
- 사용자 피드백 수집 가능
- 릴리즈 추적 및 회귀 탐지
- SaaS 제공 (인프라 관리 불필요)

#### 제약사항
- 메트릭 집계 기능 약함 (시계열 분석 어려움)
- 비용/토큰 같은 비즈니스 메트릭은 태그로만 전송 가능
- 주로 에러 추적 + 느린 요청 탐지에 적합

---

### 3. DataDog

#### 통합 가능 범위: 95%

| 메트릭 유형 | 통합 여부 | DataDog 기능 |
|------------|---------|------------|
| 파이프라인 응답시간 | ✅ | APM Traces + Metrics |
| 스테이지별 duration | ✅ | Span duration 자동 수집 |
| LLM 토큰 사용량 | ✅ | Custom metrics |
| 비용 트래킹 | ✅ | Custom metrics + Dashboard |
| 오류율 | ✅ | APM Error tracking |
| 의존성 지도 | ✅ | Service Map (OpenAI, Qdrant 호출 자동 추적) |
| 로그 통합 | ✅ | Logs ↔ Traces 연결 |
| 알림 | ✅ | Anomaly detection |

#### 통합 방법
- DataDog Java Agent 또는 `dd-trace-java`
- StatsD/DogStatsD로 커스텀 메트릭 전송

#### 구현 예시
```java
// DataDog StatsD로 비즈니스 메트릭 전송
StatsDClient statsd = new NonBlockingStatsDClientBuilder()
    .hostname("localhost")
    .port(8125)
    .prefix("rag.dialogue")
    .build();

statsd.incrementCounter("llm.tokens", totalTokens, "model:gpt-4", "type:completion");
statsd.recordExecutionTime("stage.duration", duration, "stage:tts");
statsd.gauge("cost.usd", costUsd, "service:openai");
statsd.histogram("sentence.count", sentenceCount);
```

#### 장점
- APM 자동 계측으로 스테이지별 Span 수집
- OpenAI/Qdrant 호출이 자동으로 External Service로 추적됨
- 로그 + 트레이스 + 메트릭 통합 분석 가능
- AI/ML 특화 대시보드 템플릿 제공
- Anomaly detection으로 이상 패턴 자동 탐지

#### 제약사항
- 비용 높음 (호스트/메트릭 기반 과금)
- SaaS 종속성

---

### 4. AWS CloudWatch

#### 통합 가능 범위: 70%

| 메트릭 유형 | 통합 여부 | CloudWatch 기능 |
|------------|---------|---------------|
| 파이프라인 응답시간 | ✅ | Custom metrics |
| 스테이지별 duration | ⚠️ | Dimension 제한 (최대 30개) |
| LLM 토큰 사용량 | ✅ | Metrics + Logs Insights |
| 비용 트래킹 | ✅ | Cost Explorer와 별도 관리 |
| 오류율 | ✅ | Alarms |
| 트레이싱 | ✅ | X-Ray 연동 필요 |

#### 통합 방법
- CloudWatch SDK 또는 EMF (Embedded Metric Format)
- Spring Cloud AWS로 메트릭 퍼블리싱

#### 구현 예시
```java
CloudWatchAsyncClient cloudWatch = CloudWatchAsyncClient.create();

PutMetricDataRequest request = PutMetricDataRequest.builder()
    .namespace("RAG/Dialogue")
    .metricData(MetricDatum.builder()
        .metricName("PipelineDuration")
        .value((double) durationMillis)
        .unit(StandardUnit.MILLISECONDS)
        .timestamp(Instant.now())
        .dimensions(
            Dimension.builder().name("Stage").value("LLM").build(),
            Dimension.builder().name("Status").value("SUCCESS").build()
        )
        .build())
    .build();

cloudWatch.putMetricData(request);
```

#### 장점
- AWS 생태계와 완전 통합
- Lambda, ECS와 함께 통합 모니터링 가능
- CloudWatch Insights로 로그 분석

#### 제약사항
- Dimension 개수 제한 (최대 30개)으로 스테이지별 세밀한 분석 어려움
- Custom metrics 비용이 높음
- 트레이싱은 X-Ray 별도 설정 필요
- 대시보드 커스터마이징 제한적

---

### 5. Elastic APM (ELK Stack)

#### 통합 가능 범위: 85%

| 메트릭 유형 | 통합 여부 | Elastic 기능 |
|------------|---------|------------|
| 파이프라인 응답시간 | ✅ | APM Transactions |
| 스테이지별 duration | ✅ | Spans (자동 계측) |
| LLM 토큰 사용량 | ✅ | Custom metrics + Kibana 시각화 |
| 로그 연계 | ✅ | Logs ↔ APM 통합 검색 |
| 오류 트래킹 | ✅ | Error 자동 수집 |
| 사용자 쿼리 검색 | ✅ | Elasticsearch 전문 검색 |

#### 통합 방법
- Elastic APM Java Agent
- OpenTelemetry Collector 경유

#### 장점
- 로그/메트릭/트레이스 통합 플랫폼
- LLM 출력 텍스트 전문 검색 가능 (현재 MongoDB 저장 데이터 활용)
- Kibana 대시보드 커스터마이징 자유도 높음
- 온프레미스 또는 클라우드 선택 가능

#### 제약사항
- 인프라 관리 복잡도 높음
- 대용량 데이터 처리 시 비용 증가

---

### 6. New Relic

#### 통합 가능 범위: 90%

| 메트릭 유형 | 통합 여부 | New Relic 기능 |
|------------|---------|--------------|
| 파이프라인 응답시간 | ✅ | APM Transactions |
| 스테이지별 duration | ✅ | Distributed Tracing |
| LLM 토큰 사용량 | ✅ | Custom events + NRQL 쿼리 |
| 비용 트래킹 | ✅ | Custom attributes |
| AI 특화 분석 | ✅ | ML Ops 대시보드 |
| 알림 | ✅ | NRQL 기반 알림 조건 |

#### 통합 방법
- New Relic Java Agent
- OpenTelemetry 프로토콜 지원

#### 장점
- AI/ML 모니터링 특화 기능 제공
- NRQL로 복잡한 비즈니스 로직 쿼리 가능
- 토큰 사용량 추세 분석 + 비용 예측
- 자동 이상 탐지

#### 제약사항
- 비용 높음
- SaaS 종속성

---

## 권장 통합 전략

### 최소 구성 (비용 효율)
```
Grafana + Prometheus (오픈소스)
└─ 파이프라인/스테이지 메트릭
└─ 비용/토큰 추적
└─ 알림 설정
```

**적합한 경우:**
- 스타트업 또는 MVP 단계
- 인프라 관리 가능한 팀
- 비용 최소화 필요

### 중급 구성 (에러 추적 강화)
```
Grafana + Prometheus + Sentry
├─ Grafana: 성능/비즈니스 메트릭
└─ Sentry: 에러 추적 + 느린 요청 분석
```

**적합한 경우:**
- 에러 컨텍스트 상세 추적 필요
- 사용자 피드백 수집 필요
- 릴리즈 품질 관리 중요

### 프로덕션 구성 (엔터프라이즈)
```
DataDog 또는 New Relic
└─ APM + Metrics + Logs 통합
└─ 의존성 자동 추적 (OpenAI, Qdrant)
└─ AI/ML 특화 대시보드
```

**적합한 경우:**
- 대규모 트래픽
- 24/7 운영 필요
- 인프라 관리 부담 최소화
- AI/ML 서비스 특화 분석 필요

---

## 구현 우선순위

### Phase 1: 기본 메트릭 노출
1. Micrometer 의존성 추가
2. PipelineMetricsReporter 인터페이스에 Micrometer 구현체 추가
3. DialoguePipelineTracker에 메트릭 수집 로직 통합
4. Actuator Prometheus endpoint 활성화

### Phase 2: 대시보드 구성
1. Prometheus 서버 설정 (Docker Compose)
2. Grafana 설치 및 Prometheus 데이터소스 연결
3. 기본 대시보드 구성 (파이프라인/스테이지/비용)

### Phase 3: 알림 설정
1. Alertmanager 구성
2. 핵심 알림 룰 정의 (오류율, 응답시간, 비용 임계값)
3. Slack/Email 연동

### Phase 4: 에러 추적 (선택)
1. Sentry SDK 추가
2. 에러 컨텍스트 자동 수집
3. 릴리즈 추적 설정

---

## 참고 자료

- [Micrometer 공식 문서](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Exporters](https://prometheus.io/docs/instrumenting/exporters/)
- [Grafana Dashboard 예제](https://grafana.com/grafana/dashboards/)
- [Sentry Spring Boot Integration](https://docs.sentry.io/platforms/java/guides/spring-boot/)
- [DataDog APM Java](https://docs.datadoghq.com/tracing/setup_overview/setup/java/)
