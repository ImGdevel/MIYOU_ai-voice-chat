# 모니터링 시스템 구현 문서

## 📋 구현 개요

### 목표
1. **파이프라인 병목 분석**: Stage 간 Gap, Backpressure, 데이터 크기 추적
2. **RAG 품질 모니터링**: Vector 유사도, 메모리 중요도, 검색 결과 내용 확인
3. **메모리 추출 품질**: 추출 성공률, 타입 분포, 추출된 내용 확인
4. **운영 효율성**: LLM/TTS 비용, 사용자 경험, 시스템 안정성

### 구현 범위
- **Phase 1A (CRITICAL)**: 파이프라인 병목 분석 메트릭 + 대시보드
- **Phase 1B (CRITICAL)**: RAG 품질 모니터링 메트릭 + 대시보드
- **Phase 1C (HIGH)**: LLM/대화 메트릭 + 로그 대시보드
- **Phase 2 (MEDIUM)**: 비용 추적 + UX 메트릭
- **Phase 3 (LOW)**: 시스템 안정성 + MongoDB 통합

---

## 🎯 Phase 1A: 파이프라인 병목 분석 (CRITICAL)

### 1.1 Stage Gap 메트릭

#### 목적
파이프라인 Stage 간 전환 시 발생하는 대기 시간(Gap)을 측정하여 병목 지점 파악

#### 수정 파일
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/application/monitoring/tracker/DialoguePipelineTracker.java`

**현재 상태**:
- 각 Stage의 `startedAt`, `finishedAt` 시간 저장
- Stage 실행 시간(`durationMillis`) 계산

**추가 구현**:
```java
// Stage 타임라인에서 Gap 계산
public Map<String, Long> calculateStageGaps() {
    Map<String, Long> gaps = new HashMap<>();
    List<StageMetric> sortedStages = stages.values().stream()
        .sorted(Comparator.comparing(s -> s.startedAt))
        .collect(Collectors.toList());

    for (int i = 0; i < sortedStages.size() - 1; i++) {
        StageMetric current = sortedStages.get(i);
        StageMetric next = sortedStages.get(i + 1);

        if (current.finishedAt != null && next.startedAt != null) {
            long gap = Duration.between(current.finishedAt, next.startedAt).toMillis();
            String gapKey = current.stage.name() + "_to_" + next.stage.name();
            gaps.put(gapKey, gap);
        }
    }
    return gaps;
}
```

#### 신규 생성 파일
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java`

**구현 내용**:
```java
@Configuration
public class PipelineMetricsConfiguration {

    private final MeterRegistry registry;

    public PipelineMetricsConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public MeterBinder pipelineStageGapMetrics() {
        return meterRegistry -> {
            // Stage Gap Timer는 동적으로 등록됨
            // PersistentPipelineMetricsReporter에서 호출
        };
    }

    // Gap 메트릭 등록 메서드
    public void recordStageGap(String fromStage, String toStage, long gapMillis) {
        Timer.builder("pipeline.stage.gap.duration")
             .description("파이프라인 Stage 간 전환 대기 시간")
             .tag("from_stage", fromStage)
             .tag("to_stage", toStage)
             .register(registry)
             .record(gapMillis, TimeUnit.MILLISECONDS);
    }

    // Pipeline 실행 시간 Timer
    public void recordPipelineDuration(String status, long durationMillis) {
        Timer.builder("pipeline.duration")
             .description("파이프라인 전체 실행 시간")
             .tag("status", status) // COMPLETED, FAILED, CANCELLED
             .register(registry)
             .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    // Stage별 실행 시간 Timer
    public void recordStageDuration(String stage, long durationMillis) {
        Timer.builder("pipeline.stage.duration")
             .description("파이프라인 Stage별 실행 시간")
             .tag("stage", stage)
             .register(registry)
             .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    // Pipeline 실행 카운터
    public void incrementPipelineExecution(String status) {
        Counter.builder("pipeline.executions.total")
               .description("파이프라인 실행 횟수")
               .tag("status", status)
               .register(registry)
               .increment();
    }

    // 현재 실행 중인 파이프라인 수
    private final AtomicInteger activePipelines = new AtomicInteger(0);

    @Bean
    public Gauge pipelineActiveCount() {
        return Gauge.builder("pipeline.active.count", activePipelines, AtomicInteger::get)
                    .description("현재 실행 중인 파이프라인 수")
                    .register(registry);
    }

    public void incrementActivePipelines() {
        activePipelines.incrementAndGet();
    }

    public void decrementActivePipelines() {
        activePipelines.decrementAndGet();
    }
}
```

#### 통합 지점
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/application/monitoring/monitor/PersistentPipelineMetricsReporter.java`

**수정 내용**:
```java
@Component
public class PersistentPipelineMetricsReporter implements PipelineMetricsReporter {

    private final PipelineMetricsConfiguration pipelineMetricsConfig;

    @Override
    public void reportMetrics(DialoguePipelineTracker tracker) {
        // 기존: MongoDB 저장
        UsageAnalytics analytics = buildUsageAnalytics(tracker);
        usageAnalyticsRepository.save(analytics).subscribe();

        // 신규: Micrometer 메트릭 등록
        pipelineMetricsConfig.incrementActivePipelines();

        // Pipeline 실행 시간
        long durationMillis = Duration.between(
            tracker.getPipelineStartedAt(),
            tracker.getPipelineFinishedAt()
        ).toMillis();
        pipelineMetricsConfig.recordPipelineDuration(
            tracker.getStatus().name(),
            durationMillis
        );

        // Stage별 실행 시간
        tracker.getStages().forEach((stage, metric) -> {
            if (metric.getDurationMillis() != null) {
                pipelineMetricsConfig.recordStageDuration(
                    stage.name(),
                    metric.getDurationMillis()
                );
            }
        });

        // Stage Gap 계산 및 등록
        Map<String, Long> gaps = tracker.calculateStageGaps();
        gaps.forEach((gapKey, gapMillis) -> {
            String[] parts = gapKey.split("_to_");
            pipelineMetricsConfig.recordStageGap(parts[0], parts[1], gapMillis);
        });

        // Pipeline 실행 카운터
        pipelineMetricsConfig.incrementPipelineExecution(tracker.getStatus().name());

        pipelineMetricsConfig.decrementActivePipelines();
    }
}
```

---

### 1.2 Backpressure 메트릭

#### 목적
TTS Endpoint Queue 크기, Sentence Buffer 크기 등 백프레셔 지표 수집

#### 수정 파일 1: TTS Endpoint Queue Size
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/loadbalancer/LoadBalancedSupertoneTtsAdapter.java`

**현재 상태**:
- `endpoint.incrementActiveRequests()` 호출하나 메트릭 미노출
- `endpoint.getActiveRequests()` 존재

**추가 구현**:
```java
@Component
public class LoadBalancedSupertoneTtsAdapter implements TtsSynthesizer {

    private final MeterRegistry registry;

    // TTS Endpoint Queue 메트릭 등록
    @PostConstruct
    public void registerQueueMetrics() {
        loadBalancer.getEndpoints().forEach(endpoint -> {
            // Queue size는 activeRequests와 동일 개념으로 사용
            Gauge.builder("tts.endpoint.queue.size",
                         endpoint,
                         TtsEndpoint::getActiveRequests)
                 .description("TTS 엔드포인트 대기열 크기")
                 .tag("endpoint", endpoint.getId())
                 .register(registry);
        });
    }

    // Wait time 측정
    @Override
    public Flux<AudioChunk> synthesize(String text, String speaker) {
        TtsEndpoint selectedEndpoint = loadBalancer.selectEndpoint();
        long requestStartTime = System.currentTimeMillis();

        return Mono.defer(() -> {
            long waitTime = System.currentTimeMillis() - requestStartTime;

            // Wait time 메트릭 기록
            Timer.builder("tts.endpoint.request.wait.time")
                 .description("TTS 엔드포인트 요청 대기 시간")
                 .tag("endpoint", selectedEndpoint.getId())
                 .register(registry)
                 .record(waitTime, TimeUnit.MILLISECONDS);

            return selectedEndpoint.synthesize(text, speaker);
        }).flux();
    }
}
```

#### 신규 구현: Sentence Buffer Size
**파일**: `PipelineMetricsConfiguration.java`에 추가

**구현 내용**:
```java
// Sentence Assembly Buffer 크기 추적
private final AtomicInteger sentenceBufferSize = new AtomicInteger(0);

@Bean
public Gauge sentenceBufferSizeGauge() {
    return Gauge.builder("pipeline.sentence.buffer.size",
                        sentenceBufferSize,
                        AtomicInteger::get)
                .description("Sentence Assembly 단계 버퍼 크기")
                .register(registry);
}

public void setSentenceBufferSize(int size) {
    sentenceBufferSize.set(size);
}
```

**통합 위치**: SENTENCE_ASSEMBLY Stage 처리 코드
```java
// 토큰 버퍼링 로직에서 호출
pipelineMetricsConfig.setSentenceBufferSize(tokenBuffer.size());
```

---

### 1.3 데이터 크기 메트릭

#### 목적
Stage별로 처리하는 데이터의 크기(바이트) 추정하여 메모리 사용량 파악

#### 구현
**파일**: `PipelineMetricsConfiguration.java`에 추가

```java
// Stage별 데이터 크기 Gauge
public void recordStageDataSize(String stage, String dataType, long sizeBytes) {
    Gauge.builder("pipeline.data.size.bytes",
                 () -> sizeBytes)
         .description("파이프라인 Stage별 데이터 크기")
         .tag("stage", stage)
         .tag("data_type", dataType) // memories, documents, messages
         .register(registry);
}
```

**호출 위치**: `PersistentPipelineMetricsReporter.java`
```java
// MEMORY_RETRIEVAL Stage
int memoryCount = tracker.getStageAttribute("MEMORY_RETRIEVAL", "memoryCount");
long memoryDataSize = memoryCount * 1024; // 평균 1KB로 추정
pipelineMetricsConfig.recordStageDataSize("MEMORY_RETRIEVAL", "memories", memoryDataSize);

// RETRIEVAL Stage
int documentCount = tracker.getStageAttribute("RETRIEVAL", "documentCount");
long documentDataSize = documentCount * 2048; // 평균 2KB로 추정
pipelineMetricsConfig.recordStageDataSize("RETRIEVAL", "documents", documentDataSize);

// PROMPT_BUILDING Stage
int messageCount = tracker.getStageAttribute("PROMPT_BUILDING", "messageCount");
String systemPrompt = tracker.getStageAttribute("PROMPT_BUILDING", "systemPrompt");
long promptDataSize = systemPrompt.length() * 2; // UTF-16 기준
pipelineMetricsConfig.recordStageDataSize("PROMPT_BUILDING", "messages", promptDataSize);
```

---

### 1.4 파이프라인 병목 대시보드

**파일**: `monitoring/grafana/dashboards/miyou-pipeline-bottleneck.json`

**구조**:
```json
{
  "title": "MIYOU Pipeline 병목 분석",
  "uid": "miyou-pipeline-bottleneck",
  "panels": [
    // Row 1: 파이프라인 KPI (4개 Stat 패널)
    {
      "title": "평균 파이프라인 실행 시간",
      "type": "stat",
      "targets": [{
        "expr": "rate(pipeline_duration_sum[5m]) / rate(pipeline_duration_count[5m])"
      }]
    },
    {
      "title": "파이프라인 처리량",
      "type": "stat",
      "targets": [{
        "expr": "rate(pipeline_executions_total[1m]) * 60"
      }]
    },
    {
      "title": "파이프라인 성공률",
      "type": "stat",
      "targets": [{
        "expr": "sum(rate(pipeline_executions_total{status=\"COMPLETED\"}[5m])) / sum(rate(pipeline_executions_total[5m])) * 100"
      }]
    },
    {
      "title": "현재 실행 중",
      "type": "stat",
      "targets": [{
        "expr": "pipeline_active_count"
      }]
    },

    // Row 2: Stage별 실행 시간 분석 (Heatmap + TimeSeries)
    {
      "title": "Stage별 실행 시간 분포",
      "type": "heatmap",
      "targets": [{
        "expr": "pipeline_stage_duration_bucket"
      }]
    },
    {
      "title": "Stage별 p95 실행 시간",
      "type": "timeseries",
      "targets": [{
        "expr": "histogram_quantile(0.95, pipeline_stage_duration_bucket) by (stage)"
      }]
    },

    // Row 3: Stage 간 Gap 분석 (Bar Gauge + TimeSeries)
    {
      "title": "Stage 전환 평균 Gap",
      "type": "bargauge",
      "targets": [{
        "expr": "avg(pipeline_stage_gap_duration) by (from_stage, to_stage)"
      }]
    },
    {
      "title": "Top 3 Gap 추이",
      "type": "timeseries",
      "targets": [{
        "expr": "topk(3, pipeline_stage_gap_duration{quantile=\"0.95\"})"
      }]
    },

    // Row 4: Backpressure 지표 (3개 Gauge + 1개 TimeSeries)
    {
      "title": "TTS Endpoint Queue 크기",
      "type": "gauge",
      "targets": [{
        "expr": "tts_endpoint_queue_size"
      }]
    },
    {
      "title": "Sentence Buffer 크기",
      "type": "gauge",
      "targets": [{
        "expr": "pipeline_sentence_buffer_size"
      }]
    },
    {
      "title": "Reactor Event Loop Pending Tasks",
      "type": "timeseries",
      "targets": [{
        "expr": "reactor_netty_eventloop_pending_tasks"
      }]
    },

    // Row 5: 메모리 사용량 추정 (2개 TimeSeries)
    {
      "title": "Stage별 데이터 크기",
      "type": "timeseries",
      "targets": [{
        "expr": "pipeline_data_size_bytes by (stage, data_type)"
      }]
    },
    {
      "title": "Pipeline 실행 중 Heap 증가량",
      "type": "timeseries",
      "targets": [{
        "expr": "increase(jvm_memory_used_bytes{area=\"heap\"}[30s])"
      }]
    }
  ]
}
```

---

## 🔍 Phase 1B: RAG 품질 모니터링 (CRITICAL)

### 2.1 Vector 검색 유사도 점수

#### 목적
Qdrant Vector 검색 결과의 유사도 점수(Similarity Score) 보존 및 메트릭 수집

#### 수정 파일 1: MemoryRetrievalService
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/application/dialogue/service/MemoryRetrievalService.java`

**현재 문제**:
```java
searchCandidateMemories(userId, embedding.vector(), topK)
    .map(memories -> rankAndLimit(memories, topK))  // ❌ 점수 손실
```

**해결 방법**:
```java
// Memory 클래스에 similarity score 필드 추가
@Builder
@AllArgsConstructor
public class ScoredMemory {
    private final Memory memory;
    private final float similarityScore;

    public Memory getMemory() { return memory; }
    public float getSimilarityScore() { return similarityScore; }
}

// MemoryRetrievalService 수정
public Mono<List<Memory>> retrieveRelevantMemories(String userId, String query, int topK) {
    return embeddingPort.embed(query)
        .flatMap(embedding -> searchCandidateMemories(userId, embedding.vector(), topK))
        .map(scoredMemories -> {
            // Similarity score를 Pipeline Attributes에 저장
            List<Float> similarityScores = scoredMemories.stream()
                .map(ScoredMemory::getSimilarityScore)
                .collect(Collectors.toList());

            tracker.recordStageAttribute(
                DialoguePipelineStage.MEMORY_RETRIEVAL,
                "memorySimilarityScores",
                similarityScores
            );

            // Importance score 저장
            List<Float> importanceScores = scoredMemories.stream()
                .map(sm -> sm.getMemory().getImportance())
                .collect(Collectors.toList());

            tracker.recordStageAttribute(
                DialoguePipelineStage.MEMORY_RETRIEVAL,
                "memoryImportanceScores",
                importanceScores
            );

            return rankAndLimit(scoredMemories, topK);
        })
        .map(this::groupByType);
}

// rankAndLimit 메서드 수정
private List<Memory> rankAndLimit(List<ScoredMemory> scoredMemories, int topK) {
    return scoredMemories.stream()
        .sorted(Comparator.comparing(ScoredMemory::getSimilarityScore).reversed())
        .limit(topK)
        .map(ScoredMemory::getMemory)
        .collect(Collectors.toList());
}
```

#### 수정 파일 2: SpringAiVectorDbAdapter
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/memory/adapter/vectordb/SpringAiVectorDbAdapter.java`

**수정 내용**:
```java
@Override
public Flux<ScoredMemory> search(String userId, float[] vector, int topK) {
    return Flux.fromIterable(
        vectorStore.similaritySearch(
            SearchRequest.query(vector)
                .withTopK(topK)
                .withFilterExpression(String.format("userId == '%s'", userId))
        )
    ).map(scoredPoint -> {
        Memory memory = convertToMemory(scoredPoint.getDocument());
        float score = scoredPoint.getScore();
        return new ScoredMemory(memory, score);
    });
}
```

#### 신규 생성 파일: RagQualityMetricsConfiguration
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java`

```java
@Configuration
public class RagQualityMetricsConfiguration {

    private final MeterRegistry registry;

    public RagQualityMetricsConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    // 메모리 유사도 점수 Histogram
    public void recordMemorySimilarityScore(float score, String memoryType, int rank) {
        DistributionSummary.builder("rag.memory.similarity.score")
            .description("검색된 메모리의 유사도 점수")
            .tag("memory_type", memoryType) // EXPERIENTIAL, FACTUAL
            .tag("rank", String.valueOf(rank)) // 1~topK
            .register(registry)
            .record(score);
    }

    // 메모리 중요도 Histogram
    public void recordMemoryImportance(float importance, String memoryType) {
        DistributionSummary.builder("rag.memory.importance")
            .description("검색된 메모리의 중요도")
            .tag("memory_type", memoryType)
            .register(registry)
            .record(importance);
    }

    // Candidate vs Final 메모리 개수
    public void recordFilteredMemoryCount(int candidateCount, int finalCount) {
        int filteredCount = candidateCount - finalCount;

        Counter.builder("rag.memory.filtered.count")
            .description("필터링된 메모리 개수")
            .register(registry)
            .increment(filteredCount);

        Gauge.builder("rag.memory.candidate.count", () -> candidateCount)
            .description("검색된 Candidate 메모리 개수")
            .register(registry);
    }

    // 메모리 액세스 통계
    public void recordMemoryAccessCount(int accessCount, int recencyDays) {
        DistributionSummary.builder("rag.memory.access.count")
            .description("메모리 액세스 횟수")
            .register(registry)
            .record(accessCount);

        DistributionSummary.builder("rag.memory.recency.days")
            .description("메모리 마지막 액세스 이후 경과 일수")
            .register(registry)
            .record(recencyDays);
    }
}
```

#### 통합 지점: PersistentPipelineMetricsReporter
```java
@Override
public void reportMetrics(DialoguePipelineTracker tracker) {
    // ... 기존 코드 ...

    // RAG 품질 메트릭
    List<Float> similarityScores = tracker.getStageAttribute(
        DialoguePipelineStage.MEMORY_RETRIEVAL,
        "memorySimilarityScores"
    );

    if (similarityScores != null) {
        for (int i = 0; i < similarityScores.size(); i++) {
            ragQualityMetricsConfig.recordMemorySimilarityScore(
                similarityScores.get(i),
                "EXPERIENTIAL", // 또는 Memory 객체에서 가져오기
                i + 1
            );
        }
    }

    List<Float> importanceScores = tracker.getStageAttribute(
        DialoguePipelineStage.MEMORY_RETRIEVAL,
        "memoryImportanceScores"
    );

    if (importanceScores != null) {
        for (Float importance : importanceScores) {
            ragQualityMetricsConfig.recordMemoryImportance(importance, "EXPERIENTIAL");
        }
    }

    // Candidate count (topK * 2)
    int memoryCount = tracker.getStageAttribute("MEMORY_RETRIEVAL", "memoryCount");
    ragQualityMetricsConfig.recordFilteredMemoryCount(
        memoryCount * 2,  // CANDIDATE_MULTIPLIER = 2
        memoryCount
    );
}
```

---

### 2.2 메모리 추출 품질 메트릭

#### 목적
메모리 추출 트리거, 성공률, 타입 분포, 중요도 분포 추적

#### 수정 파일: MemoryExtractionService
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/application/memory/service/MemoryExtractionService.java`

**현재 문제**:
```java
checkAndExtract(userId) // 로그만: "메모리 추출 트리거"
performExtraction(userId)
    .flatMapMany(extractionPort::extractMemories)
    .flatMap(this::saveExtractedMemory)  // ❌ 성공/실패 미추적
```

**해결 방법**:
```java
@Service
public class MemoryExtractionService {

    private final MemoryExtractionMetricsConfiguration metricsConfig;

    public Mono<Void> checkAndExtract(String userId) {
        return shouldExtract(userId)
            .filter(should -> should)
            .flatMap(should -> {
                // 트리거 메트릭 기록
                metricsConfig.incrementExtractionTriggered("conversation_count");

                return performExtraction(userId)
                    .doOnSuccess(v -> {
                        metricsConfig.incrementExtractionSuccess();
                    })
                    .doOnError(e -> {
                        metricsConfig.incrementExtractionFailure(e.getClass().getSimpleName());
                    });
            })
            .then();
    }

    private Mono<Void> performExtraction(String userId) {
        Timer.Sample sample = Timer.start();

        return conversationRepository.findRecentByUserId(userId, 10)
            .flatMapMany(extractionPort::extractMemories)
            .flatMap(extractedMemory -> {
                // 추출된 메모리 타입 및 중요도 메트릭
                metricsConfig.incrementExtractedMemoryCount(extractedMemory.getType().name());
                metricsConfig.recordExtractedMemoryImportance(
                    extractedMemory.getImportance(),
                    extractedMemory.getType().name()
                );

                return saveExtractedMemory(extractedMemory);
            })
            .then()
            .doFinally(signal -> {
                // 추출 소요 시간
                sample.stop(metricsConfig.getExtractionDurationTimer());
            });
    }
}
```

#### 신규 생성 파일: MemoryExtractionMetricsConfiguration
**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java`

```java
@Configuration
public class MemoryExtractionMetricsConfiguration {

    private final MeterRegistry registry;

    public MemoryExtractionMetricsConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    // 추출 트리거 카운터
    public void incrementExtractionTriggered(String triggerReason) {
        Counter.builder("memory.extraction.triggered")
            .description("메모리 추출 트리거 횟수")
            .tag("trigger_reason", triggerReason) // conversation_count, time_elapsed
            .register(registry)
            .increment();
    }

    // 추출 성공 카운터
    public void incrementExtractionSuccess() {
        Counter.builder("memory.extraction.success")
            .description("메모리 추출 성공 횟수")
            .register(registry)
            .increment();
    }

    // 추출 실패 카운터
    public void incrementExtractionFailure(String failureReason) {
        Counter.builder("memory.extraction.failure")
            .description("메모리 추출 실패 횟수")
            .tag("failure_reason", failureReason)
            .register(registry)
            .increment();
    }

    // 추출된 메모리 타입 카운터
    public void incrementExtractedMemoryCount(String type) {
        Counter.builder("memory.extracted.count")
            .description("추출된 메모리 개수")
            .tag("type", type) // EXPERIENTIAL, FACTUAL
            .register(registry)
            .increment();
    }

    // 추출된 메모리 중요도 Histogram
    public void recordExtractedMemoryImportance(float importance, String type) {
        DistributionSummary.builder("memory.extracted.importance")
            .description("추출된 메모리 중요도")
            .tag("type", type)
            .register(registry)
            .record(importance);
    }

    // 추출 소요 시간 Timer
    public Timer getExtractionDurationTimer() {
        return Timer.builder("memory.extraction.duration")
            .description("메모리 추출 소요 시간")
            .register(registry);
    }
}
```

---

### 2.3 RAG 품질 대시보드

**파일**: `monitoring/grafana/dashboards/miyou-rag-quality.json`

**구조**: 7 Rows, 15 Panels (상세 내용은 플랜 파일 참조)

---

## 📊 Phase 1C: LLM/대화 메트릭 + 로그 대시보드 (HIGH)

### 3.1 LLM 토큰 메트릭

**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/LlmMetricsConfiguration.java`

```java
@Configuration
public class LlmMetricsConfiguration {

    private final Counter promptTokenCounter;
    private final Counter completionTokenCounter;

    public LlmMetricsConfiguration(MeterRegistry registry) {
        this.promptTokenCounter = Counter.builder("llm.tokens.prompt")
            .description("LLM Prompt 토큰 사용량")
            .tag("model", "claude-sonnet-4.5")
            .register(registry);

        this.completionTokenCounter = Counter.builder("llm.tokens.completion")
            .description("LLM Completion 토큰 사용량")
            .tag("model", "claude-sonnet-4.5")
            .register(registry);
    }

    public void recordTokenUsage(int promptTokens, int completionTokens) {
        promptTokenCounter.increment(promptTokens);
        completionTokenCounter.increment(completionTokens);
    }
}
```

**호출 위치**: LLM 클라이언트 응답 처리
```java
// LLM 응답 처리 후
llmMetricsConfig.recordTokenUsage(
    response.getUsage().getPromptTokens(),
    response.getUsage().getCompletionTokens()
);
```

### 3.2 대화 카운터 메트릭

**파일**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/ConversationMetricsConfiguration.java`

```java
@Configuration
public class ConversationMetricsConfiguration {

    @Bean
    public MeterBinder conversationMetrics(
        ReactiveRedisTemplate<String, String> redisTemplate
    ) {
        return registry -> {
            // 일일 대화 수 Gauge
            Gauge.builder("conversation.daily.count",
                () -> getDailyCount(redisTemplate))
                .description("일일 대화 수")
                .register(registry);

            // 활성 세션 수 Gauge
            Gauge.builder("conversation.active.sessions",
                () -> getActiveSessions(redisTemplate))
                .description("활성 세션 수")
                .register(registry);
        };
    }

    private long getDailyCount(ReactiveRedisTemplate<String, String> redisTemplate) {
        String key = "conversation:daily:" + LocalDate.now();
        return redisTemplate.opsForValue().get(key)
            .map(Long::parseLong)
            .block();
    }

    private long getActiveSessions(ReactiveRedisTemplate<String, String> redisTemplate) {
        return redisTemplate.keys("session:*").count().block();
    }
}
```

---

## 📝 구현 체크리스트

### Phase 1: 기본 메트릭 노출 (Micrometer + Prometheus) ✅ 완료
- [x] `MicrometerPipelineMetricsReporter.java` 생성
  - [x] Pipeline Duration Timer 구현
  - [x] Pipeline Execution Counter 구현
  - [x] Stage Duration Timer 구현
  - [x] LLM Token Counter 구현
  - [x] TTFB/TTLB Response Latency Timer 구현
  - [x] Business Metrics (documents, sentences, audio chunks) 구현
- [x] `CompositePipelineMetricsReporter.java` 생성
  - [x] 여러 Reporter 조합 패턴 구현
- [x] `MonitoringConfiguration.java` 수정
  - [x] Composite Reporter 등록
  - [x] persistent.enabled 조건부 분기

### Phase 2: Grafana 대시보드 ✅ 완료
- [x] `miyou-pipeline.json` 대시보드 생성
  - [x] Row 1: Pipeline Overview (실행 횟수, 성공률, P50/P95)
  - [x] Row 2: Response Latency (TTFB/TTLB)
  - [x] Row 3: Stage Performance (duration, heatmap)
  - [x] Row 4: LLM Metrics (토큰 사용량)
  - [x] Row 5: Business Metrics (문장, 문서, 오디오)
- [x] Docker Compose 설정 (Prometheus, Grafana, Alertmanager)

### Phase 3: 알림 설정 ✅ 완료
- [x] `pipeline-alerts.yml` 생성
  - [x] PipelineHighErrorRate (오류율 > 5%)
  - [x] PipelineHighLatency (P95 > 10초)
  - [x] PipelineSlowTTFB (TTFB P95 > 5초)
  - [x] StageHighFailureRate (스테이지 실패율 > 10%)
  - [x] LLMTokenSpike (토큰 사용량 2배 급증)
  - [x] LLMCompletionSlow (LLM P95 > 8초)
  - [x] TTSSynthesisSlow (TTS P95 > 5초)
  - [x] PipelineLowThroughput (처리량 급감)
  - [x] PipelineDown (파이프라인 중단)
- [x] `alertmanager.yml` 설정
  - [x] Slack 채널 연동
  - [x] Critical/Warning 분리 라우팅
  - [x] Inhibit rules 설정

### Phase 4: Sentry 에러 추적 (선택) - 미구현
- [ ] Sentry 의존성 추가
- [ ] Sentry Transaction 기록 구현

### Phase 1A 고급: 파이프라인 병목 분석 - 추후 구현
- [ ] `DialoguePipelineTracker.java`: `calculateStageGaps()` 메서드 추가
- [ ] Stage Gap Timer 구현
- [ ] Backpressure 메트릭 (Sentence Buffer Size)
- [ ] `miyou-pipeline-bottleneck.json` 대시보드 생성

### Phase 1B: RAG 품질 모니터링 - 추후 구현
- [ ] `ScoredMemory` 클래스 생성
- [ ] `MemoryRetrievalService.java` 수정 (Similarity score 보존)
- [ ] `RagQualityMetricsConfiguration.java` 생성
- [ ] `miyou-rag-quality.json` 대시보드 생성

### Phase 1C: LLM/대화 메트릭 + 로그 - 추후 구현
- [ ] `LlmMetricsConfiguration.java` 생성
- [ ] `ConversationMetricsConfiguration.java` 생성
- [ ] `miyou-application-logs.json` 대시보드 생성

---

## ✅ 검증 방법

### 1. Prometheus 메트릭 노출 확인
```bash
# Phase 1A 메트릭
curl http://localhost:8080/actuator/prometheus | grep "pipeline_stage_gap_duration"
curl http://localhost:8080/actuator/prometheus | grep "pipeline_duration"
curl http://localhost:8080/actuator/prometheus | grep "tts_endpoint_queue_size"
curl http://localhost:8080/actuator/prometheus | grep "pipeline_sentence_buffer_size"
curl http://localhost:8080/actuator/prometheus | grep "pipeline_data_size_bytes"

# Phase 1B 메트릭
curl http://localhost:8080/actuator/prometheus | grep "rag_memory_similarity_score"
curl http://localhost:8080/actuator/prometheus | grep "rag_memory_importance"
curl http://localhost:8080/actuator/prometheus | grep "memory_extraction_triggered"
curl http://localhost:8080/actuator/prometheus | grep "memory_extracted_count"

# Phase 1C 메트릭
curl http://localhost:8080/actuator/prometheus | grep "llm_tokens_prompt"
curl http://localhost:8080/actuator/prometheus | grep "conversation_daily_count"
```

### 2. Grafana 쿼리 테스트
```bash
# Prometheus UI에서 쿼리 실행
http://localhost:9090/graph

# 테스트 쿼리
rate(pipeline_stage_gap_duration_sum[5m]) / rate(pipeline_stage_gap_duration_count[5m])
avg(rag_memory_similarity_score)
sum(memory_extraction_success) / (sum(memory_extraction_success) + sum(memory_extraction_failure))
```

### 3. 대시보드 UI 확인
```bash
# Grafana 접속
http://localhost:3000/admin/monitoring/grafana/

# 대시보드 확인
- MIYOU Pipeline 병목 분석
- MIYOU RAG 품질 모니터링
- MIYOU Application Logs
```

---

## 📚 참고 사항

### Micrometer Metric Naming Convention
- Prometheus 형식: `pipeline_stage_gap_duration`
- Java 형식: `pipeline.stage.gap.duration`
- Snake case로 자동 변환됨

### Histogram vs Summary
- **Histogram**: 분포 분석에 적합, Percentile 계산 가능
- **Summary**: 정확한 Percentile 계산, 집계 불가능
- 선택: Histogram 사용 (Prometheus 권장)

### Tag 사용 지침
- Stage 이름: `stage=MEMORY_RETRIEVAL`
- Status: `status=COMPLETED`
- Memory 타입: `memory_type=EXPERIENTIAL`
- 너무 많은 고유 Tag 조합은 Cardinality 증가 → 성능 저하

---

## 🚀 다음 단계

Phase 1-3 완료 후:
1. 메트릭 데이터 1-2일 수집
2. 대시보드 쿼리 성능 최적화
3. Alert 임계값 튜닝
4. Phase 1A/1B/1C 고급 기능 진행 (병목 분석, RAG 품질, 로그)

---

## 📁 구현된 파일 목록

### Java 코드
- `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java`
- `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/CompositePipelineMetricsReporter.java`
- `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MonitoringConfiguration.java` (수정)

### 모니터링 인프라
- `monitoring/docker-compose.yml`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/prometheus/rules/pipeline-alerts.yml`
- `monitoring/alertmanager/alertmanager.yml`
- `monitoring/grafana/dashboards/miyou-pipeline.json`

### 실행 방법
```bash
cd monitoring
SLACK_WEBHOOK_URL=https://hooks.slack.com/... docker-compose up -d
```

### 메트릭 확인
```bash
curl http://localhost:8080/actuator/prometheus | grep dialogue_pipeline
```
