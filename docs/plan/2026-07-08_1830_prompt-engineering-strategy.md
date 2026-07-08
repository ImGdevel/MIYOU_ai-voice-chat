# 2026-07-08 프롬프트 엔지니어링 전략 — 조립 방식 평가 및 입출력 정렬 계획

관련 코드: `SystemPromptService`, `DialogueMessageService`, `DialogueLlmStreamService`, `TokenAwareLlmAdapter`, `LlmMemoryExtractionAdapter`, `templates/**`

## 1. 이번에 고친 것 (완료)

`templates/system/common.md`, `templates/dialogue/conversation.md`가 UTF-16LE로 저장돼있는데 `FileBasedPromptTemplate`이 UTF-8로 고정 디코딩해서 실제 LLM에 깨진 텍스트가 들어가던 버그. 둘 다 UTF-8로 재저장, 컴파일 확인 완료. `common.md`는 라이브 경로(매 대화 시스템 프롬프트에 포함)라 이게 제일 심각했음.

## 2. 현재 조립 구조 요약

```
system prompt = base.md 템플릿({{persona}}/{{common}}/{{memories}}/{{context}} 치환)
  persona  : templates/system/persona/{personaId}.md, 없으면 default persona → properties.systemPrompt
  common   : templates/system/common.md (전 페르소나 공통 지침)
  memories : 코드에서 직접 조립 (experiential/factual 구분 + 날짜)
  context  : RAG 검색 문서 join
messages = [system prompt] + [과거 대화 turn들] + [현재 질문]
CompletionRequest(messages, model, stream) -> Spring AI ChatModel -> OpenAI
```
치환 엔진은 `String.replace("{{key}}", value)` 뿐, 조건부 블록(`{{#if}}`) 미지원.

## 3. 남은 문제 (우선순위순)

| # | 문제 | 위치 | 영향 |
|---|---|---|---|
| 1 | 출력 검증 전무 | `DialoguePostProcessingService` | 프롬프트가 "마크다운/불릿/이모지 금지"라고 지시해도 실제로 지켰는지 확인·교정하는 코드가 없음. 토큰 그대로 이어붙여 저장 |
| 2 | 샘플링 파라미터 미설정 | `TokenAwareLlmAdapter.streamCompletion` | `OpenAiChatOptions`에 `model`/`streamUsage`만 설정, temperature/max_tokens/stop 전부 OpenAI 기본값 — 답변 길이/일관성 통제 불가 |
| 3 | Memory 블록에 사용법 지시 없음 | `SystemPromptService.buildMemoryBlock` | "기억 데이터: ..." 라벨만 붙이고 "자연스럽게 녹여써라/직접 인용 금지" 같은 지시 없음 — persona 톤 지침에 암묵적으로만 의존 |
| 4 | 죽은 코드 | `PromptTemplatePort`/`FileBasedPromptTemplateAdapter`/`conversation.md` | 호출부 0곳. `{{#if}}` 문법도 실제로는 안 먹힘(로더가 조건부 미지원). 삭제 또는 정리 대상 |
| 5 | 컨텍스트/메모리 길이 상한 없음 | `SystemPromptService.buildContextBlock`/`buildMemoryBlock` | 검색 결과 개수 제한이 상위 정책(`MemoryRetrievalPolicy` topK 등)에 있는지는 확인했으나, 프롬프트 조립 계층 자체엔 안전장치 없음 — 정책값이 커지면 그대로 프롬프트 비대해짐 |
| 6 | 인코딩 회귀 테스트 없음 | `SystemPromptServiceTest` | `TemplateLoaderPort`를 mock으로 리터럴 문자열만 넣어 테스트해서 실제 리소스 파일 자체는 전혀 검증 안 됨 — 이번 버그가 CI에서 무증상이었던 이유 |

## 4. 참고할 만한 기존 좋은 패턴 — `LlmMemoryExtractionAdapter`

메모리 추출 경로는 이미 "입력-출력 정렬"을 제대로 하고 있다:
- 입력: `extraction-system.md`에 "Output ONLY valid JSON array" + 스키마 명시 + 예시
- 출력: `parseExtractedMemories()`에서 마크다운 코드펜스(```json) 방어적으로 벗겨내고, JSON 파싱 실패 시 `Flux.empty()`로 안전 폴백, `supersedesMemoryId` 참조 무결성까지 검증

대화 응답 경로는 이 대칭이 없다 — **입력(지시)만 있고 출력(검증)이 없는 게 근본 갭.**

## 5. 입력부/출력부 정렬 설계

### 5.1 입력부 (프롬프트 조립)
- **블록 라벨링 일관성 유지**: `기억 데이터:`/`지금 상황:`처럼 이미 라벨 있음 — 여기에 "이 데이터를 어떻게 쓸지"에 대한 1줄 메타 지시를 common.md에 추가 (예: "위 기억/상황 정보는 참고만 하고 그대로 인용하지 말 것").
- **길이 상한**: `buildMemoryBlock`/`buildContextBlock`에 최대 항목 수 또는 최대 글자수 캡 추가 — 상위 정책(topK)이 이미 개수를 제한하고 있는지 먼저 확인 필요 (정보 수집 항목, §6).
- **죽은 코드 정리**: `PromptTemplatePort` 계열 삭제할지, 실제로 쓸 계획이 있는지 결정 필요.

### 5.2 출력부 (검증/정렬)
- **경량 포맷 검증 단계 추가**: `DialoguePostProcessingService`에 memory extraction과 대칭되는 얇은 검증 스텝 도입 — 마크다운 특수문자(`#`, `*`, `` ``` ``, `- `) 잔존 여부 체크, 발견 시 메트릭 기록(반복되면 프롬프트가 안 먹힌다는 신호) + 필요시 후처리로 제거.
- **샘플링 파라미터 명시**: `OpenAiChatOptions.builder()`에 `temperature`(캐릭터 대화니까 살짝 낮은 편이 톤 일관성에 유리, 0.7~0.8대 후보) + `maxTokens`(답변 폭주 방지, 비용 통제) 추가. 페르소나별로 다른 값이 필요한지도 검토 대상.
- **강제 출력이 필요한 곳만 JSON/구조화**: 대화 응답 자체는 자유 텍스트가 맞으므로 JSON 강제는 부적절 — 대신 검증은 "사후 체크 + 로깅"으로, 메모리 추출처럼 "생성 자체를 구조화"로 가는 건 대화 응답엔 안 맞음. 이 구분을 명확히 유지.

## 6. 실행 전 수집이 필요한 정보

- [ ] `MemoryRetrievalPolicy`/RAG 검색 설정이 실제로 몇 건까지 반환하는지 (topK 등) — 프롬프트 조립 계층에 캡이 필요한지 판단 근거
- [ ] 현재 운영 중이라면(또는 로컬 테스트로) 실제 LLM 응답 샘플 몇 건 수집해서 마크다운/불릿 위반 빈도 실측 — 검증 스텝의 ROI 판단 근거
- [ ] temperature 기본값(OpenAI 미지정 시 1.0) 대비 원하는 톤 일관성 수준 — 제품 관점에서 "일관된 캐릭터" vs "다양한 답변" 우선순위 확인 필요
- [ ] `PromptTemplatePort`/`conversation.md`를 향후 쓸 계획이 있는지 (범용 프롬프트 빌더로 남겨둘지, 완전 삭제할지)

## 7. 완료 정의

- [x] 템플릿 인코딩 버그 수정 (`common.md`, `conversation.md`)
- [ ] §6 정보 수집
- [ ] Memory/Context 블록에 사용법 메타 지시 추가
- [ ] `OpenAiChatOptions`에 temperature/maxTokens 추가
- [ ] `DialoguePostProcessingService`에 경량 포맷 검증 스텝 추가
- [ ] 죽은 코드(`PromptTemplatePort` 계열) 처리 방향 결정
- [ ] 리소스 파일 자체를 로드하는 회귀 테스트 추가 (mock 아닌 실제 `FileBasedPromptTemplate` 사용)
