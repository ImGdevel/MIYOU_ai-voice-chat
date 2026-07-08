# 모니터링/메트릭 시스템 — 수집 데이터 인벤토리

이 문서는 `backend/monitoring` 모듈과 그걸 호출하는 `application`/`infrastructure`
코드가 실제로 무엇을 수집·저장·노출하는지 전수 조사한 결과다. "무엇이 있는지"가
아니라 "실제로 값이 채워지는지"까지 확인했다 — 존재하지만 죽어 있는 코드(항상 빈 값,
호출자 없음, 키 불일치로 절대 안 채워지는 속성)는 별도로 표시했다.

## 1. 전체 흐름

요청 1건(`@MonitoredPipeline` 애노테이션이 붙은 `DialoguePipelineService.executeAudioStreaming`/
`executeTextOnly`)마다 `MonitoredPipelineAspect`가 `DialoguePipelineMonitor.create()`로
`DialoguePipelineTracker` 인스턴스 하나를 만들어 리액터 컨텍스트에 심는다.
파이프라인 내부의 각 단계(`PipelineTracer`의 8개 `trace*` 메서드)는 컨텍스트에서
이 트래커를 찾아 시작/종료 시각과 속성(attribute)을 기록하고, 파이프라인이 끝나면
`PipelineSummary`를 만들어 `PipelineMetricsReporter`에 한 번 보고한다. 이 리포터가
Mongo 영속화(`PersistentPipelineMetricsReporter`)와 Prometheus 메트릭
(`MicrometerPipelineMetricsReporter`) 양쪽으로 팬아웃한다.

**주의**: `@MonitoredPipeline`이 안 붙은 메서드는 트래커 자체가 없어서 이 전체 파이프라인이
통째로 동작하지 않는다 ([fix/dialogue-audio-pipeline-tracking PR #114](https://github.com/ImGdevel/MIYOU_ai-voice-chat/pull/114)에서
`executeAudioStreaming`에 빠져 있던 걸 확인/수정함).

## 2. `DialoguePipelineStage` 8단계

`backend/monitoring/src/main/kotlin/com/miyou/app/monitoring/model/DialoguePipelineStage.kt`

| 순서 | 단계 | 설명 |
|---|---|---|
| 1 | `QUERY_PERSISTENCE` | 사용자 요청 Mongo 저장 |
| 2 | `MEMORY_RETRIEVAL` | 벡터DB에서 기억 검색 |
| 3 | `RETRIEVAL` | 문서 검색 |
| 4 | `PROMPT_BUILDING` | LLM 프롬프트 조립 |
| 5 | `LLM_COMPLETION` | LLM 토큰 생성 응답 수집 |
| 6 | `SENTENCE_ASSEMBLY` | 토큰 → 문장 결합 |
| 7 | `TTS_PREPARATION` | TTS 합성 전처리(웜업) |
| 8 | `TTS_SYNTHESIS` | Supertone TTS로 문장 → 오디오 변환 |

각 단계는 공통으로 `StageStatus`(PENDING→RUNNING→COMPLETED/FAILED/CANCELLED)와
시작/종료 `Instant`, `durationMillis`를 기록한다. 아래는 단계별 **추가** attribute다.

| 단계 | 기록 attribute | 비고 |
|---|---|---|
| `MEMORY_RETRIEVAL` | `memoryCount`, `memories`(`"[TYPE] content"` 리스트, 비어있지 않을 때만) | 실제 기억 내용이 텍스트로 남음 |
| `RETRIEVAL` | `documentCount`, `documents`(검색 문서 content 리스트) | 실제 문서 내용이 텍스트로 남음 |
| `PROMPT_BUILDING` | `systemPrompt`(시스템 메시지 전문), `messageCount` | 대화 히스토리 메시지는 개수만, 텍스트는 없음 |
| `LLM_COMPLETION` | `model`, `tokenCount`(토큰마다 +1), `promptTokens`/`completionTokens`/`totalTokens`(어댑터가 `TokenUsageProvider` 구현 시에만) | |
| `SENTENCE_ASSEMBLY` | `sentenceCount`(+1 per 문장) | 문장 자체는 파이프라인 레벨 `llmOutputs`에 별도 저장 |
| `TTS_PREPARATION` | 없음(타이밍만) | |
| `TTS_SYNTHESIS` | `audioChunks`(+1 per 청크) | |
| `QUERY_PERSISTENCE` | 없음(타이밍만) | |

## 3. 파이프라인 레벨 데이터

`DialoguePipelineTracker` (`backend/monitoring/.../monitor/DialoguePipelineTracker.kt`)

- `pipelineId` — `UUID.randomUUID()`
- `startedAt` / `finishedAt`
- `input.length`, `input.preview`(80자 넘으면 77자+`"..."`로 자름)
- `attributes: Map<String, Any>` — 파이프라인 전역 속성 bag (`"error"` = 실패 시 예외 메시지 등)
- `llmOutputs: List<String>` — 조립된 문장, **최대 20개 캡**
- `firstResponseLatencyMillis` / `lastResponseLatencyMillis` — 첫/마지막 응답 청크까지 걸린 시간
- `status: PipelineStatus` — RUNNING/COMPLETED/FAILED/CANCELLED

## 4. Mongo 영속 컬렉션 (4개)

`monitoring.persistent.enabled=true`(기본값)일 때만 저장됨.

### `performance_metrics` (`PerformanceMetricsDocument`)
`pipelineId`(PK), `status`, `startedAt`, `finishedAt`, `totalDurationMillis`,
`firstResponseLatencyMillis`, `lastResponseLatencyMillis`,
`stages: List<{stageName, status, startedAt, finishedAt, durationMillis, attributes}>`,
`systemAttributes`(파이프라인 레벨 attributes). attribute map의 key에 있는 `.`/`%`는
Mongo 필드명 제약 때문에 `%2E`/`%25`로 인코딩해서 저장.

### `usage_analytics` (`UsageAnalyticsDocument`)
`pipelineId`(PK), `status`, `timestamp`(인덱스, `llmUsage.model`과 복합 인덱스),
`userRequest{inputText, inputLength, inputPreview}`,
`llmUsage{model, promptTokens, completionTokens, totalTokens, generatedSentences, completionTimeMillis}`,
`retrievalMetrics{memoryCount, documentCount, retrievalTimeMillis}`,
`ttsMetrics{sentenceCount, audioChunks, synthesisTimeMillis, audioLengthMillis}`,
`responseMetrics{totalDurationMillis, firstResponseLatencyMillis, lastResponseLatencyMillis}`.

### `metrics_rollups` (`MetricsRollupDocument`)
`id`(`"{granularity}-{epochMilli}"`), `bucketStart`, `granularity`, `requestCount`,
`totalTokens`, `totalDurationMillis`, `avgResponseMillis`. **MINUTE 단위만 실제로 쌓임**
(아래 9절 참고).

### `stage_performance_rollups` (`StagePerformanceRollupDocument`)
`id`(`"{granularity}-{epochMilli}-{stageName}"`), `bucketStart`, `granularity`, `stageName`,
`count`, `totalDurationMillis`, `avgDurationMillis`.

## 5. 사용량/비용 계산

`CostCalculationService`(도메인) + `ModelPricing`이 `UsageMetricsInput`
(model/promptTokens/completionTokens/totalTokens/inputLength/memoryCount/documentCount/sentenceCount)을
받아 `CostInfo{llmCredits, ttsCredits, totalCredits}`를 계산한다.

- `promptTokens`가 없으면 `300 + inputLength/3 + memoryCount*50 + documentCount*100`으로 추정
- TTS는 `sentenceCount * 3000ms`로 오디오 길이를 추정해 과금
- `CREDITS_PER_DOLLAR = 10000`, LLM/임베딩/TTS 단가 테이블 하드코딩

**비용은 저장되지 않고 조회 시점에 계산됨** — `/metrics/usage/summary/total`이 최근
`UsageAnalytics` 최대 1만 건을 매번 다시 계산해서 합산한다.

## 6. Prometheus/Micrometer 메트릭

`dialogue.pipeline.*` 프리픽스로 파이프라인 지속시간/실행횟수/스테이지별 duration/
스테이지 간 gap을 기록하고, 그 외 별도 설정 클래스들이 LLM/비용/UX/RAG품질/메모리추출/
대화/TTS 엔드포인트 헬스 메트릭을 낸다. 전체 목록은 조사 결과 원문(세션 로그) 참고 —
분량이 많아 이 문서엔 요약만 남긴다:

- `llm.request.*`, `llm.success/failure.by_model`, `llm.prompt/completion.length`, `llm.response.time*`
- `llm.cost.*`, `tts.cost.*`, `cost.budget.remaining`
- `ux.response.latency.*`, `ux.satisfaction.score`(Apdex), `ux.error.*`
- `rag.memory.candidate/filtered.count`, `rag.memory.importance`
- `memory.extraction.triggered/success/failure`, `memory.extracted.count`, `memory.extracted.importance`
- `conversation.increment.count`, `conversation.query/response.length`, `conversation.response.format_violation.count`
- `tts.endpoint.credits`, `tts.endpoint.circuit_state`, `tts.endpoint.health`(**이름 중복 등록**, 8절 참고), `tts.endpoint.queue.size`, `tts.endpoint.failure.total`

## 7. 포맷 위반 감지

`ResponseFormatComplianceChecker.hasFormatViolation(response): Boolean` — 마크다운
패턴(헤더/불릿/번호목록/코드펜스) + 이모지 유니코드 범위만 검사해 **boolean 하나**만
반환. 위반 시 `conversation.response.format_violation.count` 카운터만 +1 —
**어떤 응답이 위반했는지 텍스트는 어디에도 안 남음**, 태그도 없음.

## 8. 조회 API

| 엔드포인트 | 설명 |
|---|---|
| `GET /metrics/performance?startTime&endTime` | 기간별 `PerformanceMetrics` 목록 (기본 최근 24h) |
| `GET /metrics/performance/recent?limit` | 최근 N건 |
| `GET /metrics/pipeline/{pipelineId}` | 프롬프트/메모리/문서/LLM응답 등 전체 상세 (`PerformanceMetrics` + `UsageAnalytics`) |
| `GET /metrics/rollups?granularity&limit` | MINUTE/HOUR/DAY 롤업 (HOUR/DAY는 조회 시점 재집계) |
| `GET /metrics/stages/summary?granularity&limit` | 스테이지별 평균 소요시간 |
| `GET /metrics/usage?startTime&endTime` | 기간별 사용량 |
| `GET /metrics/usage/recent?limit` | 최근 N건 사용량 |
| `GET /metrics/usage/summary?startTime&endTime` | 기간 총 요청/토큰 수 |
| `GET /metrics/usage/summary/total` | 전체 기간 총 요청/토큰/평균응답시간/크레딧 (최근 1만 건 샘플 기반 비용) |

## 9. 롤업/집계

`MetricsRollupScheduler`가 매분(`0 * * * * *`) 직전 1분 구간을 집계해
`metrics_rollups`/`stage_performance_rollups`에 MINUTE 단위로 저장. **HOUR/DAY는
저장되지 않고** 조회 시점에 MINUTE 롤업들을 다시 묶어 계산한다. 집계 실패는 로그만
남기고 조용히 넘어감(재시도 없음 → 그 구간은 데이터 구멍).

## 10. 보존기간(TTL)

**없음.** 4개 컬렉션 전부 TTL 인덱스도, 삭제 스케줄러도 없어 요청마다/분마다 무한정
쌓인다. 정리하려면 외부에서(Mongo TTL 인덱스 수동 추가, 별도 크론 등) 처리해야 함.

## 11. 알려진 문제 (개선 후보)

다음은 이번 조사 중 확인된, 실제로 죽어 있거나 값이 안 채워지는 코드다 — 모니터링
개선 작업 시 우선순위 판단용으로 남겨둠.

1. **Micrometer 쪽 attribute 키 불일치** — `MicrometerPipelineMetricsReporter`가
   `"memory.count"`/`"document.count"`/`"sentence.count"`/`"audio.chunks"`/
   `"prompt.tokens"`/`"completion.tokens"`/`"total.tokens"`/`"error.type"`를 읽지만,
   실제로 기록되는 키는 `"memoryCount"`/`"documentCount"`/`"sentenceCount"`/
   `"audioChunks"`/`"promptTokens"`/`"completionTokens"`/`"totalTokens"`/`"error"`
   (캐멀케이스, dot 없음) — 그래서 `dialogue.pipeline.memory.retrieved`,
   `dialogue.pipeline.documents.retrieved`, `dialogue.pipeline.sentences.generated`,
   `dialogue.pipeline.audio.chunks`, `llm.tokens` 카운터, 에러 타입 태그가 **항상 비어있음**.
2. **`audioLengthMillis`/`"characters"`/`"provider"`/`"cost.usd"`/`"user.id"`/
   `"memory.experiential.count"`/`"memory.factual.count"`** — 어디서도 기록 안 됨(aspirational
   코드). `rag.memory.count` 게이지, `llm.cost.usd` 게이지, 유저별 비용 태깅이 죽어있음.
3. **LLM 단가 테이블이 3곳에 중복** — `domain.cost.model.ModelPricing`,
   `MicrometerPipelineMetricsReporter.calculateLlmCost`,
   `CostTrackingMetricsConfiguration`이 서로 다른 가격표를 따로 들고 있음. 한 곳으로
   통합 필요.
4. **`tts.endpoint.health` 게이지 이름 중복 등록** — `TtsBackpressureMetrics`와
   `TtsMetricsConfiguration` 둘 다 같은 이름으로 등록하는데 `CLIENT_ERROR` 값 매핑이
   다름(-2 vs 0).
5. **호출자 없는 dead 메서드들** — `recordConversationReset`, `recordConversationByType`,
   `recordAbandonment`, `rag.memory.similarity.score`, `rag.document.relevance.score`,
   `pipeline.sentence.buffer.size`/`pipeline.data.size.bytes` 게이지,
   `CostTrackingMetricsConfiguration`의 daily/monthly reset(자동 스케줄 없음).
6. **포맷 위반 시 응답 텍스트 미보존** — 카운터만 찍혀서 어떤 응답이 왜 위반했는지
   사후 확인 불가.
7. **비용 무한 재계산** — `/metrics/usage/summary/total`이 저장된 비용 없이 매번
   최근 1만 건을 다시 계산 — 데이터 늘어날수록 비용 계산 endpoint가 느려짐.
8. **TTL 없음** — 11절 10번, 운영 장기화 시 스토리지 무한 증가.
