# MIYOU 모니터링 시스템 구현 현황

**업데이트**: 2026-02-16
**전체 진행률**: Phase 1A 완료 (100%), Phase 1B 부분 완료 (70%)

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

### Phase 1B: RAG 품질 모니터링 (70% 완료)

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

---

## ⏳ 진행 중인 작업

### Phase 1B: 서비스 통합 (30% 남음)

#### 7. MemoryRetrievalService 메트릭 통합 ⏳

**작업 내용**:
1. `RagQualityMetricsConfiguration` 의존성 주입
2. `searchCandidateMemories()` 후 candidate count 기록
3. `rankAndLimit()` 후 filtered count 기록
4. Memory importance/similarity 점수 기록
5. Pipeline attributes에 타입별 개수 추가

**예상 수정 코드**:
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

#### 8. MemoryExtractionService 메트릭 통합 ⏳

**작업 내용**:
1. `MemoryExtractionMetricsConfiguration` 의존성 주입
2. `checkAndExtract()` 호출 시 triggered 카운터 증가
3. 추출 성공/실패 기록
4. 타입별 개수 및 중요도 기록

**예상 수정 코드**:
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

#### 9. Vector Search Similarity Score 노출 ⏳

**현재 상황**:
- `SpringAiVectorDbAdapter`가 Qdrant `ScoredPoint`를 사용하여 검색
- `ScoredPoint.getScore()`에 similarity score 포함
- 현재 `Memory` 객체로 변환 시 score가 손실됨

**해결 방안 (Option B - Pipeline Attributes 사용)**:

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
| `rag.memory.similarity.score` | Distribution Summary | - | 1B | ✅ 설정 |
| `rag.memory.importance` | Distribution Summary | - | 1B | ✅ 설정 |
| `rag.memory.candidate.count` | Counter | - | 1B | ✅ 설정 |
| `rag.memory.filtered.count` | Counter | - | 1B | ✅ 설정 |
| `rag.memory.count` | Gauge | `memory_type` | 1B | ✅ |
| `rag.document.relevance.score` | Distribution Summary | - | 1B | ✅ 설정 |

### 메모리 추출 메트릭

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `memory.extraction.triggered` | Counter | - | 1B | ✅ 설정 |
| `memory.extraction.success` | Counter | - | 1B | ✅ 설정 |
| `memory.extraction.failure` | Counter | - | 1B | ✅ 설정 |
| `memory.extracted.count` | Counter | `type` | 1B | ✅ 설정 |
| `memory.extracted.importance` | Distribution Summary | - | 1B | ✅ 설정 |

### LLM 메트릭 (기존)

| 메트릭 | 타입 | Tags | Phase | 상태 |
|--------|------|------|-------|------|
| `llm.tokens` | Counter | `type`, `model` | 기존 | ✅ |
| `llm.cost.usd` | Gauge | - | 기존 | ✅ |

---

## 🎯 다음 작업 계획

### 즉시 작업 (Phase 1B 완료)

1. ⏳ **MemoryRetrievalService 메트릭 통합**
   - 예상 소요: 30분
   - 파일: MemoryRetrievalService.java
   - 의존성 주입 및 메트릭 호출 추가

2. ⏳ **MemoryExtractionService 메트릭 통합**
   - 예상 소요: 30분
   - 파일: MemoryExtractionService.java
   - 의존성 주입 및 메트릭 호출 추가

3. ⏳ **Similarity Score 노출 (Option B)**
   - 예상 소요: 20분
   - 파일: SpringAiVectorDbAdapter.java
   - Pipeline Attributes에 similarity scores 저장

4. ⏳ **검증**
   - 애플리케이션 재시작
   - `/actuator/prometheus` 확인
   - 메트릭 노출 검증

### Phase 1C: LLM/Logs (다음 단계)

1. [ ] `LlmMetricsConfiguration.java` 생성
2. [ ] `ConversationMetricsConfiguration.java` 생성
3. [ ] `miyou-application-logs.json` 대시보드 생성

### Grafana 대시보드 생성 (Phase 1 완료 후)

1. [ ] `miyou-pipeline-bottleneck.json` (5 Rows, 12 패널)
2. [ ] `miyou-rag-quality.json` (7 Rows, 15 패널)

---

## 📁 생성/수정된 파일 목록

### 생성된 파일 (5개)

1. ✅ [PipelineMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/PipelineMetricsConfiguration.java)
2. ✅ [TtsBackpressureMetrics.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/TtsBackpressureMetrics.java)
3. ✅ [RagQualityMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/RagQualityMetricsConfiguration.java)
4. ✅ [MemoryExtractionMetricsConfiguration.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/MemoryExtractionMetricsConfiguration.java)
5. ✅ [MONITORING_IMPLEMENTATION_STATUS.md](MONITORING_IMPLEMENTATION_STATUS.md) (이 문서)

### 수정된 파일 (2개)

1. ✅ [MicrometerPipelineMetricsReporter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/micrometer/MicrometerPipelineMetricsReporter.java)
   - `recordStageGapMetrics()` 추가
   - `recordRagQualityMetrics()` 추가

2. ✅ [LoadBalancedSupertoneTtsAdapter.java](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/LoadBalancedSupertoneTtsAdapter.java)
   - TtsBackpressureMetrics 통합
   - 생성자에서 엔드포인트 등록

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
