# 2026-07-08 도메인 검증 예외의 HTTP 상태코드 경계 설계

## 1. 배경

`GlobalExceptionHandler`는 의미 있는 도메인 예외(`InsufficientCreditException`, `SessionNotFoundException` 등)마다 `@ExceptionHandler`를 등록해 적절한 HTTP 상태코드로 매핑한다. 반면 Kotlin 표준 `require()`/`check()`가 던지는 `IllegalArgumentException`/`IllegalStateException`은 전용 핸들러가 없어 맨 아래 `@ExceptionHandler(Exception::class)` catch-all로 떨어져 **항상 500 INTERNAL_SERVER_ERROR**로 응답된다.

`require()`/`check()` 자체는 Kotlin 표준 관례이고 도메인 모델 invariant 체크에 계속 써야 한다 — 문제는 이게 아니다. 문제는: **이 예외가 client가 보낸 값 검증 실패인지, 서버 내부 불변조건이 깨진 것인지에 따라 400과 500 중 다른 응답이 나가야 하는데, 지금은 항상 500 하나로 뭉뚱그려진다는 것.**

### 1.1 왜 예외 타입만으로는 못 가르는가

`IllegalArgumentException`(← `require()`)과 `IllegalStateException`(← `check()`)이라는 타입 구분은 "인자 문제"와 "상태 문제"를 가른다는 점에서 신호이긴 하지만 신뢰할 수 없다. 진짜 기준은 **"이 값이 trust boundary(신뢰 경계)를 이미 통과했는가"**다.

- 경계 통과 전(=아직 검증 안 된 raw client 입력)에 처음 검증하는 지점에서 터짐 → 클라이언트 잘못 → 400
- 경계 통과 후(=DTO validation을 이미 거쳤거나 내부 계산 결과)에 터짐 → 상위 로직이 이미 보장했어야 하는 게 깨진 것 → 버그 또는 데이터 무결성 문제 → 500

### 1.2 왜 블랭킷 매핑(`IllegalArgumentException` → 무조건 400)을 하면 안 되는가

이 두 케이스를 구분하지 않고 `IllegalArgumentException`을 전부 400으로 매핑하면, 실제로는 서버 버그나 데이터 유실로 인한 내부 불변조건 위반까지 "네가 잘못 보냈다"는 응답으로 클라이언트에게 떠넘기게 된다. 반대로 지금처럼 전부 500으로 두면, 실제 클라이언트 입력 오류까지 "서버 오류"로 응답해 클라이언트가 자기 실수를 고칠 방법을 알 수 없다.

## 2. 대상 감사 (Audit)

`backend/domain/src/main/kotlin` 전체의 `require()`/`check()` 호출 45곳(22개 파일)과, `backend/api/.../dto/*Request.kt` 전체의 Bean Validation 애노테이션을 전수 대조했다.

### 2.1 확인된 갭 (client 입력이 DTO 검증 없이 도메인 require()까지 새어 들어감)

| # | 도메인 invariant | 도달 경로 | DTO 현재 상태 | 문제 |
|---|---|---|---|---|
| 1 | `ConversationSessionId`: `value.length <= 128` | `DialogueController.ragDialogueAudio/ragDialogueText` → `ConversationSessionId.of(request.sessionId)` | `RagDialogueRequest.sessionId`는 `@field:NotBlank`만 있고 길이 제한 없음 | 129자 이상 sessionId 보내면 500 (실제로는 400이어야 함) |
| 2 | `PersonaId`: `value.length <= 64`, `value.matches(Regex("^[a-zA-Z0-9_-]+$"))` | `DialogueController.createSession` → `PersonaId.ofNullable(request.personaId)` | `CreateSessionRequest.personaId`는 검증 애노테이션 전혀 없음 (빈 문자열 기본값만 있음) | 65자 이상이거나 특수문자 섞인 personaId 보내면 500 |
| 3 (경미) | `AudioTranscriptionInput`: `fileName.isNotBlank()` | `DialogueSpeechService.toTranscriptionInput` → `filePart.filename()`을 검증 없이 그대로 전달 | multipart 파일 파트라 Bean Validation 대상이 아님 — 서비스 코드에서 직접 체크 필요 | 파일명 없는 멀티파트 파트를 보내면 500 |

### 2.2 갭 아님 — 이미 정상 (경계에서 이미 막혔거나, 애초에 client 입력과 무관)

| 도메인 invariant | 상태 |
|---|---|
| `UserCredit`/`ConversationSession`의 `userId` isNotBlank + length<=128 | `CreateSessionRequest.userId`가 `@NotBlank` + `@Size(max=128)`로 정확히 동일 조건 커버 |
| `CreditTransaction.amount > 0` | `ChargeByPaymentRequest.amount`가 `@Positive`로 커버 |
| `ChargeByPaymentRequest.userId` 길이/공백 | 도메인 require()가 아니라 `UserIdResolver.resolve()`가 명시적으로 400 `ResponseStatusException`을 던짐 — 처음부터 이 패턴을 따르고 있었음 |
| `Memory`, `ExtractedMemory`, `MemoryEmbedding`, `RetrievalContext`, `RetrievalDocument`, `CompletionRequest`, `VoiceSettings`, `Voice`, `OAuthLoginResult`, `Mission`, `MissionId`, `SimilarityScore`, `ConversationTurn.query`, `Message.content`, `CreditTransactionId` | DB에서 로드되거나 내부 계산/설정값으로만 생성됨 — client가 보낸 원시값이 이 생성자에 직접 도달하는 경로가 없음. 여기서 require()가 터지면 그 자체로 버그거나 데이터 무결성 문제이므로 **500이 맞는 기본값** |

## 3. 설계

### 3.1 원칙

> **domain 계층의 `require()`/`check()` 실패는 기본값으로 500이다.** 이게 터졌다는 건 상위 계층(DTO validation, 명시적 파싱/검증 로직)이 이미 걸렀어야 하는 값이 새어 들어왔다는 뜻이고, 그 자체가 계약 위반(버그)이다.
>
> client가 보낸 원시값이 **처음으로** 도메인 생성자에 닿는 지점은 반드시 그 이전에 별도의 경계 검증(Bean Validation 또는 명시적 체크)을 둬서, 도메인 require()가 애초에 나쁜 client 입력으로는 절대 안 터지게 만든다.

이 원칙을 따르면 "이 예외가 400인가 500인가"를 예외 타입으로 매번 판단할 필요가 없어진다 — **도메인 require() 실패는 항상 500**이고, 400이 필요한 경우는 경계에서 아예 별도 로직(DTO validation 실패 → Spring이 `WebExchangeBindException`으로 자동 400 처리, 또는 도메인 예외 명시적 throw)으로 처리되기 때문에 애초에 `require()`까지 도달하지 않는다.

### 3.2 왜 블랭킷 `IllegalArgumentException → 400` 핸들러를 추가하지 않는가 (의도적 결정)

경계 검증 갭이 생기면 지금 설계에서는 500으로 터진다. 이게 오히려 장점이다 — 500은 에러 로그/알림에서 눈에 띄고, 그걸 조사하면 "아, 이건 사실 DTO 검증 갭이었네"라는 결론에 도달해 §2.1 같은 표를 다시 채우게 된다. 블랭킷 400 매핑을 걸어두면 이런 갭이 조용히 400으로 삼켜져서 아무도 눈치 못 채고 쌓인다.

### 3.3 구체적 수정 대상

1. `RagDialogueRequest.sessionId`에 `@field:Size(max = 128, message = "sessionId too long")` 추가 (`ConversationSessionId`와 동일 조건)
2. `CreateSessionRequest.personaId`에 `@field:Size(max = 64)` + `@field:Pattern(regexp = "^[a-zA-Z0-9_-]*$")` 추가 (빈 문자열은 기본값이라 허용해야 하므로 `Pattern`은 `*`로, 실제 길이 제한 위반/문자 위반만 걸러지도록 함). `PersonaId`의 정규식과 반드시 동기화 상태를 유지해야 함 — 두 곳에 나눠 적힌 조건이라 어느 한쪽만 바뀌면 다시 갭이 생긴다는 점을 주석으로 남긴다.
3. `DialogueSpeechService.toTranscriptionInput`에서 `filePart.filename()`이 blank면 `InvalidAudioFileException`(기존에 이미 있는 도메인 예외 재사용)을 명시적으로 던지도록 blank 체크 추가 — `AudioTranscriptionInput`의 require()까지 도달하기 전에 차단

### 3.4 후속 규칙 (컨벤션 문서화)

- 새 DTO 필드를 추가하고 그 값이 도메인 값객체 생성자에 들어간다면, 그 값객체의 `init { require(...) }` 조건과 DTO의 Bean Validation 애노테이션이 반드시 1:1로 맞아야 한다. 리뷰 체크리스트 항목으로 추가할 것.
- 도메인 값객체에 `require()` 조건을 추가/변경할 때는 그 값객체를 직접 생성하는 모든 컨트롤러 경로를 역추적해서 대응하는 DTO 검증도 같이 갱신한다.
- 이 짝을 자동으로 강제하는 테스트(예: 각 도메인 값객체의 경계값에 대해 DTO validation과 domain require()가 같은 시점에 막는지 확인하는 property-based 테스트)는 이번 스코프에서는 만들지 않는다 — 필요성이 더 커지면 별도 이슈로 판단.

## 4. 완료 정의

- [ ] `RagDialogueRequest.sessionId`에 길이 제한 애노테이션 추가, 129자 sessionId 요청 시 400 확인하는 테스트 추가
- [ ] `CreateSessionRequest.personaId`에 길이/패턴 애노테이션 추가, 규칙 위반 personaId 요청 시 400 확인하는 테스트 추가
- [ ] `DialogueSpeechService`에서 빈 파일명 멀티파트 업로드 시 400(`InvalidAudioFileException`) 확인하는 테스트 추가
- [ ] 이 문서의 §3.1/§3.2 원칙을 `GlobalExceptionHandler.kt` 클래스 주석 또는 인접 문서에 요약 링크로 남겨 향후 참조 가능하게 함
