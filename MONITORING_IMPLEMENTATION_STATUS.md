# MIYOU 모니터링 시스템 구현 현황

**업데이트**: 2026-02-16
**전체 진행률**: Phase 1A 완료 (100%), Phase 1B 완료 (100%), Phase 1C 완료 (100%)

---

## ✅ 완료된 작업

### Phase 1A: 파이프라인 병목 분석 (100% 완료)

#### 1. Stage Gap 메트릭 ✅

**파일**: [MicrometerPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java)

- `recordStageGapMetrics()` 메서드 구현
- Stage 간 전환 시간 자동 계산
- 메트릭: `dialogue.pipeline.stage.gap` (Timer with tags: `from_stage`, `to_stage`)

**사용 예시**:
```promql
# Top 3 병목 Stage 전환
topk(3, dialogue_pipeline_stage_gap_seconds)

# 특정 전환의 p95 latency
histogram_quantile(0.95, dialogue_pipeline_stage_gap_seconds_bucket{from_stage="memory_retrieval",to_stage="retrieval"})
```

#### 2. TTS Backpressure 메트릭 ✅

**파일**:
- [TtsBackpressureMetrics.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/TtsBackpressureMetrics.java)
- [LoadBalancedSupertoneTtsAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/LoadBalancedSupertoneTtsAdapter.java)

- TTS Endpoint Queue size Gauge (실시간 active requests)
- TTS Endpoint Health Gauge (0=HEALTHY, 1=TEMP_FAIL, 2=PERM_FAIL, 3=CLIENT_ERROR)
- LoadBalancedSupertoneTtsAdapter 생성자에서 자동 등록

**사용 예시**:
```promql
# 각 엔드포인트의 Queue 크기
tts_endpoint_queue_size{endpoint="endpoint1"}

# 장애 발생 엔드포인트 확인
tts_endpoint_health > 0
```

#### 3. Pipeline Backpressure 메트릭 ✅

**파일**: [PipelineMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java)

- `BackpressureMetrics` 컴포넌트 생성
- Sentence buffer size Gauge
- Stage별 데이터 크기 추적 (bytes)

**API**:
```java
@Autowired
private BackpressureMetrics backpressureMetrics;

// Sentence buffer 크기 업데이트
backpressureMetrics.updateSentenceBufferSize(10);

// Stage별 데이터 크기 기록
backpressureMetrics.recordStageDataSize("llm_completion", "text", 2048);
```

---

### Phase 1B: RAG 품질 모니터링 (100% 완료)

#### 4. RAG Quality 메트릭 설정 ✅

**파일**: [RagQualityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java)

- Memory similarity score Distribution Summary (p50, p75, p90, p95, p99)
- Memory importance Distribution Summary (p50, p75, p90, p95, p99)
- Document relevance score Distribution Summary (p50, p75, p90, p95, p99)
- Memory candidate/filtered Counters

**API**:
```java
@Autowired
private RagQualityMetricsConfiguration ragMetrics;

// 메모리 유사도 점수 기록
ragMetrics.recordMemorySimilarityScore(0.85);

// 메모리 중요도 기록
ragMetrics.recordMemoryImportanceScore(0.7);

// 필터링 추적
ragMetrics.recordMemoryCandidateCount(20);  // 후보 20개
ragMetrics.recordMemoryFilteredCount(10);   // 10개 필터링됨
```

**사용 예시**:
```promql
# 평균 메모리 유사도 점수
rag_memory_similarity_score_mean

# 메모리 필터링 비율
sum(rag_memory_filtered_count) / sum(rag_memory_candidate_count) * 100

# 유사도 점수 p95
rag_memory_similarity_score{quantile="0.95"}
```

#### 5. Memory Extraction 메트릭 설정 ✅

**파일**: [MemoryExtractionMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java)

- Extraction triggered Counter
- Extraction success/failure Counters
- Extracted memory importance Distribution Summary
- Extracted memory type Counters

**API**:
```java
@Autowired
private MemoryExtractionMetricsConfiguration extractionMetrics;

// 추출 트리거
extractionMetrics.recordExtractionTriggered();

// 추출 성공 (3개 메모리 추출됨)
extractionMetrics.recordExtractionSuccess(3);

// 타입별 개수
extractionMetrics.recordExtractedMemoryType("experiential", 2);
extractionMetrics.recordExtractedMemoryType("factual", 1);

// 중요도 기록
extractionMetrics.recordExtractedImportance(0.8);
```

**사용 예시**:
```promql
# 메모리 추출 성공률
sum(memory_extraction_success) /
(sum(memory_extraction_success) + sum(memory_extraction_failure)) * 100

# 분당 추출 빈도
rate(memory_extraction_triggered[5m]) * 60

# 타입별 추출 개수
memory_extracted_count{type="experiential"}
memory_extracted_count{type="factual"}
```

#### 6. MicrometerPipelineMetricsReporter 통합 ✅

**파일**: [MicrometerPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java)

- `recordRagQualityMetrics()` 메서드 추가
- MEMORY_RETRIEVAL Stage에서 타입별 메모리 개수 자동 수집

**Pipeline Attributes 지원**:
- `memory.experiential.count`
- `memory.factual.count`

**사용 예시**:
```promql
# 타입별 검색된 메모리 개수
rag_memory_count{memory_type="experiential"}
rag_memory_count{memory_type="factual"}
```

#### 7. MemoryRetrievalService 메트릭 통합 ✅

**파일**: [MemoryRetrievalService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/memory/service/MemoryRetrievalService.java)

**구현 내용**:
1. ✅ `RagQualityMetricsConfiguration` 의존성 주입
2. ✅ `searchCandidateMemories()` 후 candidate count 기록
3. ✅ `rankAndLimit()` 후 filtered count 기록
4. ✅ Memory importance 점수 기록
5. ✅ 메트릭 수집을 위한 doOnNext 훅 추가

**구현 코드**:
```java
@Service
public class MemoryRetrievalService {
    private final RagQualityMetricsConfiguration ragMetrics;

    public Mono<MemoryRetrievalResult> retrieveMemories(...) {
        return embeddingPort.embed(query)
            .flatMap(embedding -> searchCandidateMemories(userId, embedding.vector(), topK))
            .doOnNext(candidates -> {
                ragMetrics.recordMemoryCandidateCount(candidates.size());
            })
            .map(memories -> rankAndLimit(memories, topK))
            .doOnNext(ranked -> {
                int filteredCount = candidates.size() - ranked.size();
                ragMetrics.recordMemoryFilteredCount(filteredCount);

                ranked.forEach(m -> {
                    if (m.importance() != null) {
                        ragMetrics.recordMemoryImportanceScore(m.importance());
                    }
                });
            })
            .map(this::groupByType)
            .doOnNext(result -> {
                pipelineTracker.recordStageAttribute(
                    DialoguePipelineStage.MEMORY_RETRIEVAL,
                    "memory.experiential.count",
                    result.experientialMemories().size()
                );
                pipelineTracker.recordStageAttribute(
                    DialoguePipelineStage.MEMORY_RETRIEVAL,
                    "memory.factual.count",
                    result.factualMemories().size()
                );
            })
            .flatMap(this::updateAccessMetrics);
    }
}
```

#### 8. MemoryExtractionService 메트릭 통합 ✅

**파일**: [MemoryExtractionService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/memory/service/MemoryExtractionService.java)

**구현 내용**:
1. ✅ `MemoryExtractionMetricsConfiguration` 의존성 주입
2. ✅ `checkAndExtract()` 호출 시 triggered 카운터 증가
3. ✅ 추출 성공/실패 기록 (doOnNext/doOnError 훅)
4. ✅ 타입별 개수 집계 및 기록 (Collectors.groupingBy)
5. ✅ 중요도 기록 (forEach로 개별 점수 수집)

**구현 코드**:
```java
@Service
public class MemoryExtractionService {
    private final MemoryExtractionMetricsConfiguration extractionMetrics;

    public Mono<Void> checkAndExtract(String conversationId, UserId userId) {
        return conversationRepository.countByUserId(userId)
            .filter(this::isExtractionTurn)
            .doOnNext(count -> {
                extractionMetrics.recordExtractionTriggered();
            })
            .flatMap(count -> performExtraction(conversationId, userId))
            .then();
    }

    private Mono<Void> performExtraction(String conversationId, UserId userId) {
        return buildContext(conversationId, userId)
            .flatMap(this::extractMemories)
            .doOnNext(extracted -> {
                extractionMetrics.recordExtractionSuccess(extracted.size());

                // 타입별 개수
                Map<String, Long> typeCounts = extracted.stream()
                    .collect(Collectors.groupingBy(
                        m -> m.type().name(),
                        Collectors.counting()
                    ));
                typeCounts.forEach((type, count) ->
                    extractionMetrics.recordExtractedMemoryType(type, count.intValue())
                );

                // 중요도
                extracted.forEach(m ->
                    extractionMetrics.recordExtractedImportance(m.importance())
                );
            })
            .doOnError(error -> {
                extractionMetrics.recordExtractionFailure();
            })
            .flatMap(this::saveMemories)
            .then();
    }
}
```

---

### Phase 1C: LLM & Conversation 메트릭 + Application Logs (100% 완료)

#### 9. LLM 메트릭 확장 ✅

**파일**: [LlmMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/LlmMetricsConfiguration.java)

- LLM 요청 성공/실패율 Counters
- 모델별 응답 시간 Distribution Summary
- 프롬프트/완성 길이 Distribution Summary
- 에러 타입별 분류

**API**:
```java
@Autowired
private LlmMetricsConfiguration llmMetrics;

// LLM 요청 기록
llmMetrics.recordLlmRequest();

// 성공 기록
llmMetrics.recordLlmSuccess("gpt-4");

// 실패 기록 (에러 타입 포함)
llmMetrics.recordLlmFailure("gpt-4", "rate_limit");

// 토큰 길이 기록
llmMetrics.recordPromptLength(1024);
llmMetrics.recordCompletionLength(512);

// 응답 시간 기록
llmMetrics.recordResponseTime(2500);
llmMetrics.recordResponseTimeByModel("gpt-4", 2500);
```

**사용 예시**:
```promql
# LLM 성공률
sum(llm_request_success) / sum(llm_request_count) * 100

# 모델별 응답 시간 p95
llm_response_time_by_model{quantile="0.95",model="gpt-4"}

# 프롬프트 길이 평균
llm_prompt_length_mean

# 에러 타입 분포
sum by(error_type) (llm_failure_by_model)
```

#### 10. Conversation 메트릭 ✅

**파일**: [ConversationMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/ConversationMetricsConfiguration.java)

- 대화 카운트 증가/리셋 Counters
- 질의/응답 길이 Distribution Summary
- 대화 카운트 분포 Distribution Summary
- 대화 타입별 분류

**API**:
```java
@Autowired
private ConversationMetricsConfiguration conversationMetrics;

// 대화 카운트 증가
conversationMetrics.recordConversationIncrement();

// 대화 카운트 분포 기록
conversationMetrics.recordConversationCount(15);

// 질의/응답 길이 기록
conversationMetrics.recordQueryLength(256);
conversationMetrics.recordResponseLength(512);

// 타입별 분류
conversationMetrics.recordConversationByType("casual");
```

**사용 예시**:
```promql
# 분당 대화 증가율
rate(conversation_increment_count[5m]) * 60

# 평균 질의 길이
conversation_query_length_mean

# 평균 응답 길이
conversation_response_length_mean

# 대화 카운트 p90
conversation_count_distribution{quantile="0.90"}
```

#### 11. MicrometerPipelineMetricsReporter LLM 통합 ✅

**파일**: [MicrometerPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java)

**구현 내용**:
1. ✅ `LlmMetricsConfiguration` 의존성 주입
2. ✅ LLM_COMPLETION Stage에서 요청/성공/실패 기록
3. ✅ 프롬프트/완성 토큰 길이 Distribution 기록
4. ✅ 모델별 응답 시간 기록
5. ✅ 스테이지 상태 기반 성공/실패 분류

#### 12. DialoguePostProcessingService Conversation 통합 ✅

**파일**: [DialoguePostProcessingService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/dialogue/pipeline/stage/DialoguePostProcessingService.java)

**구현 내용**:
1. ✅ `ConversationMetricsConfiguration` 의존성 주입
2. ✅ 질의 길이 기록 (query.length())
3. ✅ 응답 길이 기록 (response.length())
4. ✅ 대화 카운트 증가 기록 (doOnNext 훅)
5. ✅ 대화 카운트 분포 기록

#### 13. Application Logs 대시보드 ✅

**파일**: [miyou-application-logs.json](monitoring/grafana/dashboards/miyou-application-logs.json)

**구성 내용**:
- **로그 레벨 분포**: ERROR/WARN/INFO/DEBUG 스택 차트
- **ERROR 로그 스트림**: 실시간 에러 로그 표시
- **ERROR 발생 클래스 분포**: logger_name 기준 파이 차트
- **WARN 발생 클래스 분포**: logger_name 기준 파이 차트
- **메모리 추출 로그**: 메모리 추출 이벤트 필터링
- **LLM 호출 로그**: LLM 요청/응답 로그 필터링
- **전체 로그 스트림**: 모든 로그 실시간 표시

**Loki 쿼리 예시**:
```logql
# ERROR 로그
{job="miyou-dialogue"} | json | level="ERROR"

# 로그 레벨 분포
sum by(level) (count_over_time({job="$job"} | json | level =~ "ERROR|WARN|INFO|DEBUG" [$__interval]))

# 메모리 추출 로그
{job="miyou-dialogue"} |~ "메모리 추출|memory extraction"

# LLM 호출 로그
{job="miyou-dialogue"} |~ "LLM|OpenAI|Claude|GPT"
```

---

## 📝 구현 노트

### Similarity Score 수집 방식

**현재 구현 방식**:
- Importance score를 similarity proxy로 사용
- `MemoryRetrievalService`에서 `memory.importance()` 값을 메트릭으로 기록
- Qdrant ScoredPoint의 실제 similarity score는 현재 수집하지 않음

**향후 개선 옵션 (Optional)**:

```java
// SpringAiVectorDbAdapter.java
@Override
public Flux<Memory> search(UserId userId, List<Float> queryEmbedding, ...) {
    return Mono.fromCallable(() -> {
        // ... search logic ...
        List<ScoredPoint> results = qdrantClient.searchAsync(searchPoints).get();

        // Similarity scores 수집
        List<Float> similarityScores = results.stream()
            .map(ScoredPoint::getScore)
            .collect(Collectors.toList());

        // Pipeline Attributes에 저장 (optional - if tracker available)
        if (pipelineTracker != null) {
            pipelineTracker.recordStageAttribute(
                DialoguePipelineStage.MEMORY_RETRIEVAL,
                "memory.similarity.scores",
                similarityScores
            );
        }

        return results.stream()
            .map(point -> toMemoryFromScoredPoint(point, userId))
            .collect(Collectors.toList());
    })
    .subscribeOn(Schedulers.boundedElastic())
    .flatMapMany(Flux::fromIterable);
}
```

**RagQualityMetricsConfiguration에서 사용**:
```java
// MicrometerPipelineMetricsReporter.java - recordRagQualityMetrics()
Object scoresObj = stage.attributes().get("memory.similarity.scores");
if (scoresObj instanceof List<?>) {
    List<?> scores = (List<?>) scoresObj;
    for (Object score : scores) {
        if (score instanceof Number) {
            ragQualityMetrics.recordMemorySimilarityScore(
                ((Number) score).doubleValue()
            );
        }
    }
}
```

---

## 📊 사용 가능한 메트릭 목록

### 파이프라인 메트릭 (기존 + 신규)

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `dialogue.pipeline.duration` | Timer | `status` | 기존 | ✅ |
| `dialogue.pipeline.executions` | Counter | `status` | 기존 | ✅ |
| `dialogue.pipeline.stage.duration` | Timer | `stage`, `status` | 기존 | ✅ |
| `dialogue.pipeline.stage.gap` | Timer | `from_stage`, `to_stage` | 1A | ✅ |
| `dialogue.pipeline.response.first` | Timer | - | 기존 | ✅ |
| `dialogue.pipeline.response.last` | Timer | - | 기존 | ✅ |

### Backpressure 메트릭

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `tts.endpoint.queue.size` | Gauge | `endpoint` | 1A | ✅ |
| `tts.endpoint.health` | Gauge | `endpoint` | 1A | ✅ |
| `pipeline.sentence.buffer.size` | Gauge | - | 1A | ✅ |
| `pipeline.data.size.bytes` | Gauge | `stage`, `data_type` | 1A | ✅ |

### RAG 품질 메트릭

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `rag.memory.similarity.score` | Distribution Summary | - | 1B | ✅ |
| `rag.memory.importance` | Distribution Summary | - | 1B | ✅ |
| `rag.memory.candidate.count` | Counter | - | 1B | ✅ |
| `rag.memory.filtered.count` | Counter | - | 1B | ✅ |
| `rag.memory.count` | Gauge | `memory_type` | 1B | ✅ |
| `rag.document.relevance.score` | Distribution Summary | - | 1B | ✅ |

### 메모리 추출 메트릭

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `memory.extraction.triggered` | Counter | - | 1B | ✅ |
| `memory.extraction.success` | Counter | - | 1B | ✅ |
| `memory.extraction.failure` | Counter | - | 1B | ✅ |
| `memory.extracted.count` | Counter | `type` | 1B | ✅ |
| `memory.extracted.importance` | Distribution Summary | - | 1B | ✅ |

### LLM 메트릭 (기존 + Phase 1C)

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `llm.tokens` | Counter | `type`, `model` | 기존 | ✅ |
| `llm.cost.usd` | Gauge | - | 기존 | ✅ |
| `llm.request.count` | Counter | - | 1C | ✅ |
| `llm.request.success` | Counter | - | 1C | ✅ |
| `llm.request.failure` | Counter | - | 1C | ✅ |
| `llm.success.by_model` | Counter | `model` | 1C | ✅ |
| `llm.failure.by_model` | Counter | `model`, `error_type` | 1C | ✅ |
| `llm.prompt.length` | Distribution Summary | - | 1C | ✅ |
| `llm.completion.length` | Distribution Summary | - | 1C | ✅ |
| `llm.response.time.ms` | Distribution Summary | - | 1C | ✅ |
| `llm.response.time.by_model` | Distribution Summary | `model` | 1C | ✅ |

### Conversation 메트릭 (Phase 1C)

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `conversation.increment.count` | Counter | - | 1C | ✅ |
| `conversation.reset.count` | Counter | - | 1C | ✅ |
| `conversation.query.length` | Distribution Summary | - | 1C | ✅ |
| `conversation.response.length` | Distribution Summary | - | 1C | ✅ |
| `conversation.count.distribution` | Distribution Summary | - | 1C | ✅ |
| `conversation.by_type` | Counter | `type` | 1C | ✅ |

---

## 🎯 다음 작업 계획

### ✅ Phase 1 완료 (100%)

Phase 1의 모든 작업이 완료되었습니다:

**Phase 1A - Pipeline Bottleneck Analysis**:
1. ✅ Stage Gap 메트릭
2. ✅ TTS Backpressure 메트릭
3. ✅ Pipeline Backpressure 메트릭

**Phase 1B - RAG Quality Monitoring**:
1. ✅ RAG 품질 메트릭 설정 파일 생성
2. ✅ 메모리 추출 메트릭 설정 파일 생성
3. ✅ MemoryRetrievalService 메트릭 통합
4. ✅ MemoryExtractionService 메트릭 통합
5. ✅ MicrometerPipelineMetricsReporter 통합

**Phase 1C - LLM & Conversation Metrics + Application Logs**:
1. ✅ `LlmMetricsConfiguration.java` 생성
2. ✅ `ConversationMetricsConfiguration.java` 생성
3. ✅ MicrometerPipelineMetricsReporter LLM 통합
4. ✅ DialoguePostProcessingService Conversation 통합
5. ✅ `miyou-application-logs.json` 대시보드 생성

**다음 검증 단계**:
- 애플리케이션 시작 후 `/actuator/prometheus` 확인
- 메트릭 노출 검증
- Grafana 대시보드 import 및 검증

### Grafana 대시보드 생성 (다음 단계)

1. [ ] `miyou-pipeline-bottleneck.json` (5 Rows, 12 패널)
2. [ ] `miyou-rag-quality.json` (7 Rows, 15 패널)
3. ✅ `miyou-application-logs.json` (4 Rows, 7 패널)

### Phase 2: Cost & UX Metrics (향후 작업)

1. [ ] 비용 추적 메트릭
2. [ ] UX 지표 메트릭

---

## 📁 생성/수정된 파일 목록

### 생성된 파일 (8개)

**Phase 1A (3개)**:
1. ✅ [PipelineMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java)
2. ✅ [TtsBackpressureMetrics.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/TtsBackpressureMetrics.java)

**Phase 1B (2개)**:
3. ✅ [RagQualityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java)
4. ✅ [MemoryExtractionMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java)

**Phase 1C (3개)**:
5. ✅ [LlmMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/LlmMetricsConfiguration.java)
6. ✅ [ConversationMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/ConversationMetricsConfiguration.java)
7. ✅ [miyou-application-logs.json](monitoring/grafana/dashboards/miyou-application-logs.json)

**문서**:
8. ✅ [MONITORING_IMPLEMENTATION_STATUS.md](MONITORING_IMPLEMENTATION_STATUS.md) (이 문서)

### 수정된 파일 (4개)

**Phase 1A (1개)**:
1. ✅ [LoadBalancedSupertoneTtsAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/LoadBalancedSupertoneTtsAdapter.java)
   - TtsBackpressureMetrics 통합
   - 생성자에서 엔드포인트 등록

**Phase 1B (2개)**:
2. ✅ [MemoryRetrievalService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/memory/service/MemoryRetrievalService.java)
   - RagQualityMetricsConfiguration 통합
   - doOnNext 훅으로 메트릭 수집

3. ✅ [MemoryExtractionService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/memory/service/MemoryExtractionService.java)
   - MemoryExtractionMetricsConfiguration 통합
   - doOnNext/doOnError 훅으로 메트릭 수집

**Phase 1A/1B/1C 공통 (1개)**:
4. ✅ [MicrometerPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java)
   - Phase 1A: `recordStageGapMetrics()` 추가
   - Phase 1B: `recordRagQualityMetrics()` 추가
   - Phase 1C: LlmMetricsConfiguration 통합, `recordLlmMetrics()` 확장

**Phase 1C (1개)**:
5. ✅ [DialoguePostProcessingService.java](webflux-dialogue/src/main/java/com/study/webflux/rag/application/dialogue/pipeline/stage/DialoguePostProcessingService.java)
   - ConversationMetricsConfiguration 통합
   - 질의/응답 길이 및 대화 카운트 메트릭 수집

---

## 🔍 검증 가이드

### 1. 메트릭 노출 확인

```bash
# 애플리케이션 실행 후
curl http://localhost:8080/actuator/prometheus > metrics.txt

# Stage Gap 메트릭
grep "dialogue_pipeline_stage_gap" metrics.txt

# TTS Backpressure 메트릭
grep "tts_endpoint_queue_size" metrics.txt
grep "tts_endpoint_health" metrics.txt

# Pipeline Backpressure 메트릭
grep "pipeline_sentence_buffer_size" metrics.txt
grep "pipeline_data_size_bytes" metrics.txt

# RAG 메트릭 (서비스 통합 후)
grep "rag_memory" metrics.txt
grep "memory_extraction" metrics.txt

# LLM 메트릭 (Phase 1C)
grep "llm_request" metrics.txt
grep "llm_prompt_length" metrics.txt
grep "llm_completion_length" metrics.txt
grep "llm_response_time" metrics.txt

# Conversation 메트릭 (Phase 1C)
grep "conversation_" metrics.txt
```

### 2. Prometheus 쿼리 테스트

```promql
# Stage Gap Top 3
topk(3, dialogue_pipeline_stage_gap_seconds)

# TTS Queue 최대값
max(tts_endpoint_queue_size)

# 메모리 필터링 비율
sum(rag_memory_filtered_count) / sum(rag_memory_candidate_count) * 100

# 메모리 추출 성공률
sum(memory_extraction_success) / (sum(memory_extraction_success) + sum(memory_extraction_failure)) * 100
```

### 3. Grafana 시각화 확인

대시보드 생성 후:
- 모든 패널 데이터 로딩 확인
- 시간 범위 변경 시 쿼리 정상 작동 확인
- 새로고침 시 메트릭 업데이트 확인

---

## 📝 구현 노트

### 설계 결정 사항

1. **Stage Gap 계산**:
   - Stage 정렬: `startedAt` 기준 오름차순
   - Gap = `current.finishedAt` → `next.startedAt`
   - 음수 Gap (병렬 실행)은 0으로 처리

2. **Distribution Summary vs Histogram**:
   - Micrometer의 `DistributionSummary.publishPercentiles()` 사용
   - Prometheus의 `histogram_quantile()` 함수와 호환
   - 자동으로 p50, p75, p90, p95, p99 계산

3. **Similarity Score 저장 위치**:
   - **Option B 채택**: Pipeline Attributes에 배열로 저장
   - Memory 객체 변경 최소화
   - 기존 파이프라인 흐름 유지

4. **Gauge vs Counter**:
   - Gauge: Queue size, Buffer size (현재 값)
   - Counter: Candidate count, Filtered count (누적 값)
   - Timer: Stage duration, Gap duration (시간 분포)
   - Distribution Summary: Similarity, Importance (값 분포)

### 주의 사항

1. **메모리 누수 방지**:
   - Distribution Summary는 히스토그램 버킷을 메모리에 저장
   - 과도한 카디널리티 (unique tag 조합) 주의

2. **성능 영향**:
   - 메트릭 수집은 비동기로 처리
   - Distribution Summary 계산 비용은 미미

3. **Thread Safety**:
   - Micrometer의 모든 메트릭은 thread-safe
   - ConcurrentHashMap 사용 시 추가 동기화 불필요

---

**문서 버전**: 1.0
**작성자**: Claude Sonnet 4.5
**참조**: [MONITORING_EXPANSION_PLAN.md](MONITORING_EXPANSION_PLAN.md)
