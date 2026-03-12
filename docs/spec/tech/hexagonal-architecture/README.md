# MIYOU 헥사고날 아키텍처 구조도

현재 구현된 `webflux-dialogue` 모듈의 헥사고날(포트-어댑터) 아키텍처를 시각화한 문서입니다.
리팩토링 진행 현황은 [hexagonal-architecture-refactoring](../hexagonal-architecture-refactoring/README.md)을 참고하세요.

---

## 1. 개요

### 아키텍처 원칙

| 원칙 | 설명 |
|------|------|
| Dependency Inversion | 모든 의존성은 Infrastructure → Application → Domain 방향으로만 흐른다 |
| Port-Adapter Pattern | Domain이 선언한 Port(인터페이스)를 Infrastructure Adapter가 구현한다 |
| Framework Independence | Domain 레이어는 Spring, MongoDB, Micrometer 등 외부 프레임워크에 의존하지 않는다 |
| Reactive First | 모든 I/O는 `Mono`/`Flux`를 사용하며 블로킹 호출을 금지한다 |

### 레이어 요약

| 레이어 | 파일 수 | 역할 |
|--------|---------|------|
| Domain | 63 | 순수 비즈니스 로직, 포트 인터페이스, 도메인 모델 |
| Application | 27 | 유스케이스 오케스트레이션, 파이프라인 구현, AOP 모니터링 |
| Infrastructure | 85 | REST 컨트롤러, 외부 시스템 어댑터, Spring 설정 |

---

## 2. 레이어 의존성 구조도

```mermaid
graph TB
    subgraph INF["Infrastructure Layer (85 files)"]
        direction TB
        CTRL["Inbound Controllers\nDialogueController\nConversationSessionController\nMetricsController\nDashboardController"]
        ADAPT_LLM["LLM Adapter\nSpringAiLlmAdapter\nTokenAwareLlmAdapter"]
        ADAPT_TTS["TTS Adapter\nLoadBalancedSupertoneTtsAdapter\nTtsLoadBalancer"]
        ADAPT_STT["STT Adapter\nOpenAiWhisperSttAdapter"]
        ADAPT_MEM["Memory Adapters\nSpringAiVectorDbAdapter\nSpringAiEmbeddingAdapter\nRedisConversationCounterAdapter\nLlmMemoryExtractionAdapter"]
        ADAPT_RETR["Retrieval Adapter\nInMemoryRetrievalAdapter"]
        ADAPT_DB["Persistence Adapters\nConversationMongoAdapter\nConversationSessionMongoAdapter"]
        ADAPT_MON["Monitoring Adapters\nMicrometerPipelineMetricsReporter\nPersistentPipelineMetricsReporter\nMongoUsageAnalyticsRepository"]
        CFG["Configuration\nRagDialogueProperties\nDialoguePolicyConfiguration\n13x MonitoringConfiguration"]
    end

    subgraph APP["Application Layer (27 files)"]
        direction TB
        PIPE["Dialogue Pipeline\nDialoguePipelineService"]
        STAGE["Pipeline Stages\nDialogueInputService\nDialogueLlmStreamService\nDialogueMessageService\nDialogueTtsStreamService\nDialoguePostProcessingService\nSystemPromptService"]
        SVC_MEM["Memory Services\nMemoryExtractionService\nMemoryRetrievalService"]
        POLICY["Policies\nDialogueExecutionPolicy\nPromptTemplatePolicy\nSttPolicy\nMemoryExtractionPolicy"]
        MON_APP["Monitoring\nDialoguePipelineMonitor\nDialoguePipelineTracker\nPipelineTracer\nMetricsQueryService\nMonitoredPipelineAspect"]
    end

    subgraph DOM["Domain Layer (63 files)"]
        direction TB
        PORT_DLG["Dialogue Ports\nDialoguePipelineUseCase\nLlmPort / TtsPort / SttPort\nConversationRepository\nConversationSessionRepository"]
        PORT_MEM["Memory Ports\nVectorMemoryPort\nEmbeddingPort\nMemoryExtractionPort\nConversationCounterPort"]
        PORT_MON["Monitoring Ports\nPipelineMetricsReporter\nMetricsQueryUseCase\nPerformanceMetricsRepository\nUsageAnalyticsRepository"]
        PORT_RETR["Retrieval Port\nRetrievalPort"]
        MODELS["Domain Models\ndialogue / memory /\nretrieval / voice /\nmonitoring / cost"]
    end

    CTRL -->|calls| PIPE
    ADAPT_LLM -->|implements| PORT_DLG
    ADAPT_TTS -->|implements| PORT_DLG
    ADAPT_STT -->|implements| PORT_DLG
    ADAPT_MEM -->|implements| PORT_MEM
    ADAPT_RETR -->|implements| PORT_RETR
    ADAPT_DB -->|implements| PORT_DLG
    ADAPT_MON -->|implements| PORT_MON
    PIPE -->|implements| PORT_DLG
    PIPE -->|uses| STAGE
    PIPE -->|uses| SVC_MEM
    PIPE -->|uses| POLICY
    STAGE -->|calls| PORT_DLG
    STAGE -->|calls| PORT_MEM
    STAGE -->|calls| PORT_RETR
    MON_APP -->|calls| PORT_MON
    MON_APP -->|reads| MODELS

    classDef infra fill:#FFA94D,stroke:#E8590C,color:#1C1C1C
    classDef app fill:#74C69D,stroke:#2D6A4F,color:#1C1C1C
    classDef domain fill:#74C0FC,stroke:#1971C2,color:#1C1C1C

    class CTRL,ADAPT_LLM,ADAPT_TTS,ADAPT_STT,ADAPT_MEM,ADAPT_RETR,ADAPT_DB,ADAPT_MON,CFG infra
    class PIPE,STAGE,SVC_MEM,POLICY,MON_APP app
    class PORT_DLG,PORT_MEM,PORT_MON,PORT_RETR,MODELS domain
```

---

## 3. 도메인 서브도메인 구조도

6개 서브도메인의 포트와 어댑터 연결을 나타냅니다.

```mermaid
graph LR
    subgraph DIALOGUE["dialogue"]
        direction TB
        D_LLM["LlmPort"]
        D_TTS["TtsPort"]
        D_STT["SttPort"]
        D_CONV["ConversationRepository"]
        D_SESS["ConversationSessionRepository"]
        D_TMPL["PromptTemplatePort\nTemplateLoaderPort"]
        D_TOKEN["TokenUsageProvider"]
        D_UC["DialoguePipelineUseCase"]
    end

    subgraph MEMORY["memory"]
        direction TB
        M_VEC["VectorMemoryPort"]
        M_EMB["EmbeddingPort"]
        M_EXT["MemoryExtractionPort"]
        M_RETR["MemoryRetrievalPort"]
        M_CNT["ConversationCounterPort"]
    end

    subgraph RETRIEVAL["retrieval"]
        R_PORT["RetrievalPort"]
    end

    subgraph VOICE["voice"]
        V_PORT["VoiceSelectionPort"]
    end

    subgraph MONITORING["monitoring"]
        direction TB
        MON_RPT["PipelineMetricsReporter"]
        MON_QRY["MetricsQueryUseCase"]
        MON_PERF["PerformanceMetricsRepository"]
        MON_USG["UsageAnalyticsRepository"]
        MON_RLP["MetricsRollupRepository\nStagePerformanceRollupRepository"]
    end

    subgraph COST["cost"]
        COST_SVC["CostCalculationService"]
    end

    D_LLM -->|impl| LLM_ADAPT["SpringAiLlmAdapter\nTokenAwareLlmAdapter"]
    D_TTS -->|impl| TTS_ADAPT["LoadBalancedSupertoneTtsAdapter"]
    D_STT -->|impl| STT_ADAPT["OpenAiWhisperSttAdapter"]
    D_CONV -->|impl| MONGO_ADAPT["ConversationMongoAdapter"]
    D_SESS -->|impl| SESS_ADAPT["ConversationSessionMongoAdapter"]
    D_TMPL -->|impl| TMPL_ADAPT["FileBasedPromptTemplateAdapter"]
    M_VEC -->|impl| VEC_ADAPT["SpringAiVectorDbAdapter\n(Qdrant)"]
    M_EMB -->|impl| EMB_ADAPT["SpringAiEmbeddingAdapter\n(OpenAI)"]
    M_EXT -->|impl| EXT_ADAPT["LlmMemoryExtractionAdapter"]
    M_CNT -->|impl| CNT_ADAPT["RedisConversationCounterAdapter"]
    R_PORT -->|impl| RETR_ADAPT["InMemoryRetrievalAdapter"]
    MON_RPT -->|impl| MON_ADAPT["MicrometerPipelineMetricsReporter\nPersistentPipelineMetricsReporter\nLoggingPipelineMetricsReporter\n(Composite)"]
    MON_PERF -->|impl| PERF_ADAPT["MongoPerformanceMetricsRepository"]
    MON_USG -->|impl| USG_ADAPT["MongoUsageAnalyticsRepository"]

    classDef port fill:#74C0FC,stroke:#1971C2,color:#1C1C1C
    classDef adapter fill:#FFA94D,stroke:#E8590C,color:#1C1C1C

    class D_LLM,D_TTS,D_STT,D_CONV,D_SESS,D_TMPL,D_TOKEN,D_UC port
    class M_VEC,M_EMB,M_EXT,M_RETR,M_CNT port
    class R_PORT,V_PORT port
    class MON_RPT,MON_QRY,MON_PERF,MON_USG,MON_RLP port
    class LLM_ADAPT,TTS_ADAPT,STT_ADAPT,MONGO_ADAPT,SESS_ADAPT,TMPL_ADAPT adapter
    class VEC_ADAPT,EMB_ADAPT,EXT_ADAPT,CNT_ADAPT,RETR_ADAPT adapter
    class MON_ADAPT,PERF_ADAPT,USG_ADAPT adapter
```

---

## 4. RAG 파이프라인 플로우

```mermaid
sequenceDiagram
    participant Client
    participant DC as DialogueController
    participant PIPE as DialoguePipelineService
    participant INPUT as DialogueInputService
    participant MSG as DialogueMessageService
    participant SYS as SystemPromptService
    participant LLM as DialogueLlmStreamService
    participant ASSEMBLE as SentenceAssembler
    participant TTS as DialogueTtsStreamService
    participant POST as DialoguePostProcessingService

    participant MONGO as ConversationMongoAdapter
    participant RETR as InMemoryRetrievalAdapter
    participant QDRANT as SpringAiVectorDbAdapter
    participant OPENAI as SpringAiLlmAdapter
    participant SUPERTONE as LoadBalancedSupertoneTtsAdapter
    participant MEM_EXT as LlmMemoryExtractionAdapter

    Client->>DC: POST /rag/dialogue (text or audio)
    DC->>PIPE: execute(PipelineInputs)

    rect rgb(240, 248, 255)
        Note over PIPE,INPUT: Stage 1: Input Preparation
        PIPE->>INPUT: prepareContext()
        INPUT->>MONGO: findRecentConversations(last 10)
        MONGO-->>INPUT: ConversationTurn[]
        INPUT->>RETR: retrieve(query, conversations)
        RETR-->>INPUT: RetrievalContext (keyword match)
        INPUT->>QDRANT: searchSimilar(embedding, threshold)
        QDRANT-->>INPUT: MemoryRetrievalResult[]
        INPUT-->>PIPE: ConversationContext
    end

    rect rgb(240, 255, 240)
        Note over PIPE,SYS: Stage 2: Prompt Assembly
        PIPE->>MSG: buildMessages(context)
        MSG->>SYS: buildSystemPrompt(policy, context)
        SYS-->>MSG: systemPrompt (from template)
        MSG-->>PIPE: Message[] (system + history + user)
    end

    rect rgb(255, 248, 220)
        Note over PIPE,LLM: Stage 3: LLM Streaming
        PIPE->>LLM: streamTokens(messages)
        LLM->>OPENAI: streamCompletion(CompletionRequest)
        OPENAI-->>LLM: Flux<token>
        LLM->>ASSEMBLE: buffer tokens into sentences
        ASSEMBLE-->>LLM: Flux<sentence>
    end

    rect rgb(255, 240, 240)
        Note over PIPE,TTS: Stage 4: TTS Synthesis (per sentence)
        loop Each sentence
            PIPE->>TTS: synthesize(sentence, voice)
            TTS->>SUPERTONE: POST /v2/text-to-speech (load balanced)
            SUPERTONE-->>TTS: audio bytes (WAV/MP3)
            TTS-->>Client: Flux<DataBuffer> (streaming audio chunk)
        end
    end

    rect rgb(248, 240, 255)
        Note over PIPE,POST: Stage 5: Post Processing (async)
        PIPE->>POST: persist and extract memories
        POST->>MONGO: saveConversationTurn(turn)
        POST->>MEM_EXT: extractMemories (every 5 conversations)
        MEM_EXT->>OPENAI: extractMemoriesFromLLM()
        MEM_EXT->>QDRANT: upsertMemoryEmbeddings()
    end
```

---

## 5. TTS 로드밸런서 구조

```mermaid
graph TB
    TTS_PORT["TtsPort (Domain Port)"]
    LB_ADAPT["LoadBalancedSupertoneTtsAdapter\n(implements TtsPort)"]
    LB["TtsLoadBalancer\n(Round-Robin Selection)"]
    CREDIT["TtsCreditMonitor\n(Credit Health Checks)"]
    BREAKER["EndpointCircuitBreaker\n(Per Endpoint)"]

    EP1["TtsEndpoint 1\nsupertone.ai:443"]
    EP2["TtsEndpoint 2\nsupertone.ai:443"]
    EP3["TtsEndpoint 3\nsupertone.ai:443"]
    EP4["TtsEndpoint 4\nsupertone.ai:443"]
    EP5["TtsEndpoint 5\nsupertone.ai:443"]

    CLASSIFIER["TtsErrorClassifier\n(Retry / Skip / Fail)"]
    BACKOFF["ExponentialBackoffStrategy\n(Retry Delays)"]
    EVENTS["TtsEndpointFailureEvent\nTtsLowCreditEvent\n(Spring ApplicationEvents)"]
    METRICS["TtsEndpointMetricsRegistrar\n(Micrometer)"]

    TTS_PORT -->|calls| LB_ADAPT
    LB_ADAPT -->|selects via| LB
    LB -->|monitors| CREDIT
    LB_ADAPT -->|on failure| CLASSIFIER
    CLASSIFIER -->|retry with| BACKOFF
    LB -->|manages| EP1 & EP2 & EP3 & EP4 & EP5
    BREAKER -->|guards| EP1 & EP2 & EP3 & EP4 & EP5
    LB_ADAPT -->|publishes| EVENTS
    EVENTS -->|triggers| METRICS

    classDef domain fill:#74C0FC,stroke:#1971C2,color:#1C1C1C
    classDef core fill:#FFA94D,stroke:#E8590C,color:#1C1C1C
    classDef support fill:#B2F2BB,stroke:#2D6A4F,color:#1C1C1C
    classDef endpoint fill:#FFD8A8,stroke:#E8590C,color:#1C1C1C

    class TTS_PORT domain
    class LB_ADAPT,LB,CREDIT core
    class CLASSIFIER,BACKOFF,BREAKER,EVENTS,METRICS support
    class EP1,EP2,EP3,EP4,EP5 endpoint
```

---

## 6. 전체 패키지 구조

```
com.study.webflux.rag/
│
├── RagApplication.java
│
├── domain/                                    # Domain Layer (63 files)
│   ├── cost/
│   │   ├── model/
│   │   │   ├── CostInfo.java
│   │   │   └── ModelPricing.java
│   │   └── service/
│   │       └── CostCalculationService.java
│   │
│   ├── dialogue/
│   │   ├── model/
│   │   │   ├── AudioTranscriptionInput.java
│   │   │   ├── CompletionRequest.java
│   │   │   ├── CompletionResponse.java
│   │   │   ├── ConversationContext.java
│   │   │   ├── ConversationSession.java
│   │   │   ├── ConversationSessionId.java
│   │   │   ├── ConversationTurn.java
│   │   │   ├── Message.java
│   │   │   ├── MessageRole.java
│   │   │   ├── PersonaId.java
│   │   │   ├── TokenUsage.java
│   │   │   └── UserId.java
│   │   ├── port/
│   │   │   ├── ConversationRepository.java
│   │   │   ├── ConversationSessionRepository.java
│   │   │   ├── DialoguePipelineUseCase.java
│   │   │   ├── LlmPort.java
│   │   │   ├── PromptTemplatePort.java
│   │   │   ├── SttPort.java
│   │   │   ├── TemplateLoaderPort.java
│   │   │   ├── TokenUsageProvider.java
│   │   │   └── TtsPort.java
│   │   └── service/
│   │       └── SentenceAssembler.java
│   │
│   ├── memory/
│   │   ├── model/
│   │   │   ├── ExtractedMemory.java
│   │   │   ├── Memory.java
│   │   │   ├── MemoryEmbedding.java
│   │   │   ├── MemoryExtractionContext.java
│   │   │   ├── MemoryRetrievalResult.java
│   │   │   └── MemoryType.java
│   │   └── port/
│   │       ├── ConversationCounterPort.java
│   │       ├── EmbeddingPort.java
│   │       ├── MemoryExtractionPort.java
│   │       ├── MemoryRetrievalPort.java
│   │       └── VectorMemoryPort.java
│   │
│   ├── monitoring/
│   │   ├── model/
│   │   │   ├── DialoguePipelineStage.java
│   │   │   ├── MetricsGranularity.java
│   │   │   ├── MetricsRollup.java
│   │   │   ├── PerformanceMetrics.java
│   │   │   ├── PipelineDetail.java
│   │   │   ├── PipelineStatus.java
│   │   │   ├── PipelineSummary.java
│   │   │   ├── StagePerformanceRollup.java
│   │   │   ├── StagePerformanceSummary.java
│   │   │   ├── StageSnapshot.java
│   │   │   ├── StageStatus.java
│   │   │   └── UsageAnalytics.java
│   │   └── port/
│   │       ├── MetricsQueryUseCase.java
│   │       ├── MetricsRollupRepository.java
│   │       ├── PerformanceMetricsRepository.java
│   │       ├── PipelineMetricsReporter.java
│   │       ├── StagePerformanceRollupRepository.java
│   │       └── UsageAnalyticsRepository.java
│   │
│   ├── retrieval/
│   │   ├── model/
│   │   │   ├── RetrievalContext.java
│   │   │   ├── RetrievalDocument.java
│   │   │   └── SimilarityScore.java
│   │   └── port/
│   │       └── RetrievalPort.java
│   │
│   └── voice/
│       ├── model/
│       │   ├── AudioFormat.java
│       │   ├── Voice.java
│       │   ├── VoiceSettings.java
│       │   └── VoiceStyle.java
│       └── port/
│           └── VoiceSelectionPort.java
│
├── application/                               # Application Layer (27 files)
│   ├── dialogue/
│   │   ├── pipeline/
│   │   │   ├── stage/
│   │   │   │   ├── DialogueInputService.java
│   │   │   │   ├── DialogueLlmStreamService.java
│   │   │   │   ├── DialogueMessageService.java
│   │   │   │   ├── DialoguePostProcessingService.java
│   │   │   │   ├── DialogueTtsStreamService.java
│   │   │   │   └── SystemPromptService.java
│   │   │   ├── DialoguePipelineService.java
│   │   │   └── PipelineInputs.java
│   │   ├── policy/
│   │   │   ├── DialogueExecutionPolicy.java
│   │   │   ├── PromptTemplatePolicy.java
│   │   │   └── SttPolicy.java
│   │   └── service/
│   │       └── DialogueSpeechService.java
│   │
│   ├── memory/
│   │   ├── policy/
│   │   │   ├── MemoryExtractionPolicy.java
│   │   │   └── MemoryRetrievalPolicy.java
│   │   └── service/
│   │       ├── MemoryExtractionService.java
│   │       └── MemoryRetrievalService.java
│   │
│   └── monitoring/
│       ├── aop/
│       │   ├── MonitoredPipeline.java
│       │   └── MonitoredPipelineAspect.java
│       ├── context/
│       │   └── PipelineContext.java
│       ├── monitor/
│       │   ├── DialoguePipelineMonitor.java
│       │   └── DialoguePipelineTracker.java
│       ├── port/
│       │   ├── ConversationMetricsPort.java
│       │   ├── MemoryExtractionMetricsPort.java
│       │   └── RagQualityMetricsPort.java
│       └── service/
│           ├── MetricsQueryService.java
│           ├── MetricsRollupScheduler.java
│           └── PipelineTracer.java
│
└── infrastructure/                            # Infrastructure Layer (85 files)
    ├── common/
    │   ├── config/
    │   │   ├── ClockConfig.java
    │   │   ├── OpenApiConfiguration.java
    │   │   └── WebFluxCorsConfiguration.java
    │   ├── constants/
    │   │   └── DialogueConstants.java
    │   └── template/
    │       ├── FileBasedPromptTemplate.java
    │       └── FileBasedPromptTemplateAdapter.java
    │
    ├── dialogue/
    │   ├── adapter/
    │   │   ├── llm/
    │   │   │   ├── SpringAiLlmAdapter.java
    │   │   │   └── TokenAwareLlmAdapter.java
    │   │   ├── persistence/
    │   │   │   ├── document/
    │   │   │   │   ├── ConversationDocument.java
    │   │   │   │   └── ConversationSessionDocument.java
    │   │   │   ├── ConversationMongoAdapter.java
    │   │   │   └── ConversationSessionMongoAdapter.java
    │   │   ├── stt/
    │   │   │   └── OpenAiWhisperSttAdapter.java
    │   │   └── tts/
    │   │       ├── loadbalancer/
    │   │       │   ├── circuit/
    │   │       │   │   ├── CircuitBreakerState.java
    │   │       │   │   ├── EndpointCircuitBreaker.java
    │   │       │   │   └── ExponentialBackoffStrategy.java
    │   │       │   ├── CreditResponse.java
    │   │       │   ├── TtsCreditMonitor.java
    │   │       │   ├── TtsEndpoint.java
    │   │       │   ├── TtsEndpointFailureEvent.java
    │   │       │   ├── TtsErrorClassifier.java
    │   │       │   ├── TtsLoadBalancer.java
    │   │       │   └── TtsLowCreditEvent.java
    │   │       ├── LoadBalancedSupertoneTtsAdapter.java
    │   │       ├── SupertoneConfig.java
    │   │       └── SupertoneTtsAdapter.java
    │   ├── config/
    │   │   ├── properties/
    │   │   │   └── RagDialogueProperties.java
    │   │   ├── DialoguePolicyConfiguration.java
    │   │   ├── DialogueVoiceConfiguration.java
    │   │   ├── DomainServiceConfiguration.java
    │   │   ├── PersonaVoiceProvider.java
    │   │   ├── SttConfiguration.java
    │   │   └── TtsConfiguration.java
    │   └── repository/
    │       ├── ConversationMongoRepository.java
    │       └── ConversationSessionMongoRepository.java
    │
    ├── inbound/
    │   └── web/
    │       ├── dialogue/
    │       │   ├── docs/
    │       │   │   └── DialogueApi.java
    │       │   ├── dto/
    │       │   │   ├── CreateSessionRequest.java
    │       │   │   ├── CreateSessionResponse.java
    │       │   │   ├── RagDialogueRequest.java
    │       │   │   ├── SttDialogueResponse.java
    │       │   │   └── SttTranscriptionResponse.java
    │       │   ├── ConversationSessionController.java
    │       │   └── DialogueController.java
    │       └── monitoring/
    │           ├── DashboardController.java
    │           └── MetricsController.java
    │
    ├── memory/
    │   ├── adapter/
    │   │   ├── LlmMemoryExtractionAdapter.java
    │   │   ├── MemoryExtractionConfig.java
    │   │   ├── MemoryExtractionDto.java
    │   │   ├── RedisConversationCounterAdapter.java
    │   │   ├── SpringAiEmbeddingAdapter.java
    │   │   └── SpringAiVectorDbAdapter.java
    │   └── config/
    │       ├── MemoryConfiguration.java
    │       └── QdrantCollectionInitializer.java
    │
    ├── monitoring/
    │   ├── adapter/
    │   │   ├── MongoMetricsRollupRepository.java
    │   │   ├── MongoPerformanceMetricsRepository.java
    │   │   ├── MongoStagePerformanceRollupRepository.java
    │   │   └── MongoUsageAnalyticsRepository.java
    │   ├── config/
    │   │   ├── ConversationMetricsConfiguration.java
    │   │   ├── CostTrackingMetricsConfiguration.java
    │   │   ├── HttpServerMetricsFilterConfiguration.java
    │   │   ├── LlmMetricsConfiguration.java
    │   │   ├── MemoryExtractionMetricsConfiguration.java
    │   │   ├── MonitoringConfiguration.java
    │   │   ├── PipelineMetricsConfiguration.java
    │   │   ├── RagQualityMetricsConfiguration.java
    │   │   ├── TtsBackpressureMetrics.java
    │   │   ├── TtsEndpointMetricsRegistrar.java
    │   │   ├── TtsMetricsConfiguration.java
    │   │   ├── TtsMonitoringEventListener.java
    │   │   └── UxMetricsConfiguration.java
    │   ├── document/
    │   │   ├── MetricsRollupDocument.java
    │   │   ├── PerformanceMetricsDocument.java
    │   │   ├── StagePerformanceRollupDocument.java
    │   │   └── UsageAnalyticsDocument.java
    │   ├── micrometer/
    │   │   ├── CompositePipelineMetricsReporter.java
    │   │   └── MicrometerPipelineMetricsReporter.java
    │   └── repository/
    │       ├── SpringDataMetricsRollupRepository.java
    │       ├── SpringDataPerformanceMetricsRepository.java
    │       ├── SpringDataStagePerformanceRollupRepository.java
    │       └── SpringDataUsageAnalyticsRepository.java
    │
    ├── outbound/
    │   └── monitoring/
    │       ├── LoggingPipelineMetricsReporter.java
    │       └── PersistentPipelineMetricsReporter.java
    │
    └── retrieval/
        └── adapter/
            ├── InMemoryRetrievalAdapter.java
            ├── KeywordSimilaritySupport.java
            └── VectorMemoryRetrievalAdapter.java
```
