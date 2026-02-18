# 메모리 관리 & RAG 시스템

## 전체 흐름

```mermaid
flowchart TD
    subgraph Retrieval["검색 단계 (요청 시)"]
        Q["사용자 입력"] --> EMB["임베딩 생성\ntext-embedding-3-small"]
        EMB --> VRET["Qdrant 벡터 검색\ntopK=5"]
        VRET --> SCORE["중요도 가중 스코어링\nscore × (1 + importance × boostFactor)"]
        SCORE --> FILT["임계값 필터\nimportance < 0.3 제거"]
        FILT --> MEM["MemoryRetrievalResult"]
    end

    subgraph Extraction["추출 단계 (N회 대화마다)"]
        CNT["대화 카운터\n(Redis)"] -->|N회 도달| LOAD["최근 대화 이력 로드\n(MongoDB)"]
        LOAD --> LLM["LLM 메모리 추출\nGPT-4o-mini"]
        LLM --> EMB2["임베딩 생성"]
        EMB2 --> STORE["Qdrant 저장\n(importance 포함)"]
    end

    MEM --> Pipeline["파이프라인\n시스템 프롬프트에 주입"]
```

---

## 메모리 데이터 모델

```
Memory
 ├── id          UUID
 ├── userId      UserId
 ├── personaId   PersonaId        ← 사용자별 메모리 격리
 ├── content     String           ← 추출된 핵심 정보
 ├── importance  float (0.0~1.0)  ← LLM이 판단한 중요도
 ├── embedding   float[1536]      ← 벡터 표현
 └── createdAt   Instant
```

`PersonaId` Value Object로 동일 사용자가 여러 페르소나(캐릭터)를 가질 때 메모리를 독립적으로 관리합니다.

---

## 벡터 검색 스코어링

단순 코사인 유사도 검색 대신 **중요도 가중치**를 반영한 복합 스코어링:

```
finalScore = cosineSimilarity × (1 + importance × boostFactor)
```

- 유사도가 같아도 중요도가 높은 메모리가 우선 반환됩니다.
- `importance < 0.3` 메모리는 결과에서 필터링하여 노이즈를 제거합니다.

---

## RAG 파이프라인

```mermaid
sequenceDiagram
    participant U as 사용자
    participant P as PipelineService
    participant Q as Qdrant
    participant O as OpenAI

    U->>P: 질문 입력
    par 병렬 실행
        P->>Q: RAG 문서 검색 (topK=3)
        P->>Q: 메모리 검색 (topK=5)
        P->>P: 대화 이력 로드
    end
    Q-->>P: 관련 문서 + 메모리
    P->>P: 시스템 프롬프트 조립\n(문서 + 메모리 + 대화 이력 주입)
    P->>O: LLM 스트리밍 요청
    O-->>U: 토큰 스트리밍 → TTS → 오디오
```

---

## 메모리 추출 트리거

```mermaid
flowchart LR
    REQ["대화 요청"] --> INC["Redis 카운터 증가"]
    INC --> CHK{N회 도달?}
    CHK -->|No| SKIP["스킵"]
    CHK -->|Yes| RESET["카운터 리셋"]
    RESET --> EXTRACT["비동기 메모리 추출\n(응답 후 백그라운드)"]
    EXTRACT --> STORE["Qdrant 저장"]
```

메모리 추출은 응답 스트리밍 완료 후 백그라운드에서 실행되어 사용자 응답 지연에 영향을 주지 않습니다.

---

## 관련 문서

- [아키텍처 개요](./01-architecture.md)
- [RAG 파이프라인 초기 설계](../architecture/rag-voice-pipeline.md)
