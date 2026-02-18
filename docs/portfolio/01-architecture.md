# 아키텍처 설계

## 전체 시스템 구성

```mermaid
graph TB
    Client["클라이언트"]

    subgraph App["Spring WebFlux Application"]
        Controller["DialogueController\n POST /rag/dialogue/audio"]
        Pipeline["DialoguePipelineService\n(오케스트레이션)"]

        subgraph Stages["파이프라인 스테이지"]
            S1["① 입력 준비\nRAG + 메모리 병렬 검색"]
            S2["② 시스템 프롬프트 조립"]
            S3["③ LLM 토큰 스트리밍"]
            S4["④ 문장 조립 → TTS 합성"]
            S5["⑤ 오디오 스트리밍 응답"]
            S6["⑥ 후처리(비동기)\n대화 저장 + 메모리 추출"]
        end
    end

    subgraph Infra["인프라"]
        MongoDB[("MongoDB\n대화 이력·메트릭")]
        Redis[("Redis\n세션 카운터")]
        Qdrant[("Qdrant\n벡터 메모리")]
        OpenAI["OpenAI API\nGPT-4o-mini · Embedding"]
        Supertone["Supertone TTS\n5개 엔드포인트"]
    end

    Client -->|HTTP Streaming| Controller
    Controller --> Pipeline
    Pipeline --> S1 --> S2 --> S3 --> S4 --> S5
    S5 -.->|concatWith| S6

    S1 -->|벡터 검색| Qdrant
    S3 -->|LLM 호출| OpenAI
    S4 -->|TTS 합성| Supertone
    S6 --> MongoDB
    S6 -->|임베딩 저장| Qdrant
    Pipeline -->|카운터| Redis
```

---

## 헥사고날 아키텍처 (Ports & Adapters)

### Before → After 리팩토링

기존 구조는 단일 `voice/` 패키지에 모든 로직이 혼재하여 LLM 프로바이더 교체 시 서비스 코드 수정이 불가피했습니다.

```mermaid
graph LR
    subgraph Before["Before - 직접 의존"]
        C1[Controller] --> S1[PipelineService]
        S1 --> L1[FakeLlmClient]
        S1 --> T1[SupertoneTtsClient]
    end

    subgraph After["After - 의존성 역전"]
        C2[Controller] -->|interface| UC[UseCase Port]
        UC -.->|구현| S2[PipelineService]
        S2 -->|interface| LP[LlmPort]
        S2 -->|interface| TP[TtsPort]
        S2 -->|interface| VP[VectorMemoryPort]
        LP -.->|구현| OA[OpenAiAdapter]
        TP -.->|구현| SA[SupertoneAdapter]
        VP -.->|구현| QA[QdrantAdapter]
    end
```

### 계층 구조

```
domain/
 ├── port/
 │   ├── LlmPort               ← LLM 추상화
 │   ├── TtsPort               ← TTS 추상화
 │   ├── VectorMemoryPort      ← 벡터 DB 추상화
 │   ├── RetrievalPort         ← RAG 검색 추상화
 │   └── ConversationRepository
 └── model/                    ← 불변 Value Object (Java Record)
     ├── dialogue/  ConversationTurn, PersonaId
     ├── memory/    Memory, ExtractedMemory
     └── voice/     Voice, VoiceSettings, AudioFormat

application/
 ├── dialogue/  DialoguePipelineService, DialogueController
 └── memory/    MemoryExtractionService, MemoryRetrievalService

infrastructure/
 ├── dialogue/adapter/  OpenAiLlmAdapter, SupertoneTtsAdapter
 ├── memory/adapter/    QdrantVectorAdapter, RedisCounterAdapter
 └── retrieval/adapter/ VectorMemoryRetrievalAdapter
```

**핵심 원칙**: 도메인 레이어는 `reactor-core`(`Mono`/`Flux`)만 허용, Spring 프레임워크 의존 없음.
LLM·TTS·VectorDB 교체 시 해당 Adapter 파일만 수정하면 됩니다.

---

## 반응형 파이프라인 상세

### 입력 준비 단계 — `Mono.zip` 병렬 실행

```mermaid
sequenceDiagram
    participant P as PipelineService
    participant Q as Qdrant (RAG)
    participant M as Qdrant (Memory)
    participant DB as MongoDB

    P->>+Q: retrieve(query, topK=3)
    P->>+M: retrieveMemories(query, topK=5)
    P->>+DB: loadConversationHistory()
    Note over P,DB: 세 작업이 동시에 실행됨 (Mono.zip)
    Q-->>-P: RetrievalContext (50ms)
    DB-->>-P: ConversationContext (30ms)
    M-->>-P: MemoryRetrievalResult (100ms)
    P->>P: PipelineInputs 조합 (총 100ms)
```

순차 실행(180ms) → 병렬 실행(100ms): **44% 단축**

### 문장 조립 → TTS 선기동

```mermaid
sequenceDiagram
    participant LLM as OpenAI (LLM)
    participant ASM as SentenceAssembler
    participant TTS as Supertone (TTS)
    participant Client as 클라이언트

    LLM-->>ASM: "안녕"
    LLM-->>ASM: "하세요"
    LLM-->>ASM: ". "
    ASM->>+TTS: "안녕하세요." (문장 완성 즉시)
    LLM-->>ASM: "오늘"
    TTS-->>Client: audio chunk 1
    LLM-->>ASM: "날씨가"
    LLM-->>ASM: "좋네요."
    ASM->>+TTS: "오늘 날씨가 좋네요."
    TTS-->>Client: audio chunk 2
```

LLM 응답 완료를 기다리지 않고 문장 단위로 TTS를 선기동하여 체감 지연을 최소화합니다.

### 후처리 비동기 분리

```java
// 응답 스트림과 후처리를 concatWith로 연결
// 클라이언트는 오디오를 받은 뒤 후처리가 백그라운드에서 실행됨
audioStream.concatWith(
    postProcessingService.process(inputs, llmResponse)
        .then(Mono.empty())
)
```

---

## SOLID 원칙 적용

| 원칙 | 적용 내용 |
|------|----------|
| **SRP** | `PipelineService`(오케스트레이션) · `LlmAdapter`(LLM 통신) · `SentenceAssembler`(문장 조립) 분리 |
| **OCP** | `LlmPort` 인터페이스로 새 LLM 추가 시 기존 코드 수정 불필요 |
| **LSP** | 모든 `LlmPort` 구현체(OpenAI·Claude·Gemini stub)는 동일하게 교체 가능 |
| **ISP** | `LlmPort`, `TtsPort`, `VectorMemoryPort` 각각 단일 역할만 정의 |
| **DIP** | `PipelineService`는 구체 클래스가 아닌 Port 인터페이스에 의존 |

---

## 관련 문서

- [아키텍처 Before/After 상세 비교](../architecture/ARCHITECTURE_COMPARISON.md)
- [리팩토링 진행 현황](../refactoring/REFACTORING_STATUS.md)
- [RAG 파이프라인 초기 설계](../architecture/rag-voice-pipeline.md)
