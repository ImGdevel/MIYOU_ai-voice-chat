# 2026-07-07 리팩토링 후보 전수 조사

전체 코드베이스(backend 5개 모듈 + frontend)를 계층별 서브에이전트 5개로 병렬 탐색해 리팩토링 후보를 정리한다. 각 항목은 실제 파일:줄 확인 후 기록한 것만 포함한다.

관련 GitHub 이슈: `refactoring` 라벨로 주제별 11개 등록 (본 문서 하단 매핑 참고).

## 1. Credit 로직 중복 + 포트 이름 충돌 (최우선)

- `backend/domain/.../dialogue/port/CreditChargingPort.kt` vs `backend/domain/.../mission/port/CreditChargingPort.kt` — 동일 이름 인터페이스가 다른 시그니처로 두 패키지에 존재.
- `CreditApplicationService.kt:74-99, 109-134, 144-167, 201-225` — deduct/refund/charge/grantReward 4개 메서드가 `findByUserId → transform → save` 패턴 반복 (5회).
- `CreditApplicationService.kt:79-83 vs 113-118` — 실패 처리 예외 타입 불일치 (InsufficientCreditException vs IllegalStateException).
- `CreditApplicationService.kt:206-225` — grantMissionReward가 chargeByPayment(144-167)와 사실상 동일 로직, 메서드만 분리.
- `CreditApplicationService.kt:235-248` — initializeIfAbsent만 DuplicateKeyException 처리, 다른 save 호출은 처리 없음 (동시요청 시 일부 실패 가능).

**해결 방향**: 포트 이름 분리(`DialogueCreditChargingPort`/`MissionCreditChargingPort`), `performCreditOperation()` 공통 함수로 통합, 예외 타입 통일, upsert 패턴 도입.

## 2. 예외 처리 패턴 전방위 불일치

- `GlobalExceptionHandler.kt:85-116` — 에러코드 결정이 reason 문자열 키워드 매칭(brittle).
- `GlobalExceptionHandler.kt:49-62` — ResponseStatusException 단순 래핑만, 도메인 예외 핸들러 부재.
- `CreditController.kt:69-77`, `AuthController.kt:31-33`, `DialogueController.kt:113-114,134-135` — 컨트롤러마다 ResponseStatusException 수동 생성 또는 onErrorMap 인라인 처리.
- `DialogueSpeechService.kt` — 동기 throw(57-71)와 Mono.error(34-39) 혼용.
- domain/auth — 예외 클래스가 domain과 exception 모듈에 분산.

**해결 방향**: 도메인 예외 정의 → `GlobalExceptionHandler`에 `@ExceptionHandler` 매핑 일괄 등록, 컨트롤러의 ResponseStatusException/onErrorMap 산발 사용 제거, 모든 검증을 Mono 기반으로 통일.

## 3. App.tsx God Component

- `frontend/src/App.tsx` 908줄, state 17개, useRef 7개, API 함수 4개 최상위 정의.
- 에러 처리 5곳(54-60, 480, 653 등) 제각각.
- 모달 상태 관리 방식이 App.tsx와 Sidebar.tsx에서 서로 다름.
- `Sidebar.tsx:12-23` — props 9개 drilling.
- `streamAudioAndPlay():160-298` — 콜백/내부 상태 타입 미정의.
- `App.tsx:375-392` — patchMessage + throttle 로직 직접 구현.
- `App.tsx:30-32` — 크레딧 비용/녹음 임계값 등 상수 산재.

**해결 방향**: API 계층(`services/api.ts`) 분리, `useRecording()`/`useApiCall()`/`useThrottledCallback()` 훅 추출, Context/Zustand로 상태 분리, `constants.ts` 신설.

## 4. MetricsController 책임 과다

- `MetricsController.kt:30-180` — 엔드포인트 9개(성능/사용량/비용/롤업/파이프라인 상세 전부 처리).

**해결 방향**: `PerformanceMetricsController`/`UsageAnalyticsController`/`CostAnalyticsController` 3분할.

## 5. DialogueController 책임 과다

- `DialogueController.kt:31-155` — 포트 4개 주입, 세션관리/오디오스트리밍/텍스트스트리밍/STT 로직 혼재.
- `DialogueController.kt:58-65` — personaId 기본값 처리(도메인 정책)가 REST 계층에 노출.
- `DialogueController.kt:75-77` — DTO 매핑 inline, `from()` 팩토리 패턴과 불일치.
- `DialogueController.kt:66` — 인증 체크 방식이 다른 컨트롤러(UserIdResolver)와 다름.

**해결 방향**: `AudioDialogueService`/`TextDialogueService`로 내부 분할, personaId 기본값을 도메인/DTO로 이동, `CreateSessionResponse.from()` 팩토리 추가, 인증 체크 방식 표준화.

## 6. Memory 도메인 서비스 분리

- `Memory.kt:7-62` 143줄 — decay, archive, ranking 로직 단일 클래스에 혼재.
- `MemoryRetrievalService.kt:178-182` — 정책 상수(RECENCY_WEIGHT 등)가 companion object에만 존재, MemoryExtractionService와 중복 가능성.
- `VectorMemoryPort.kt` (findAllActive, applyDecayAndArchive) — 배치 감쇠 로직이 포트에 노출.
- `MemoryExtractionService.kt:47-55` — checkAndExtract의 이중 조건 체크 의도 불명확.

**해결 방향**: decay/archive 정책을 별도 도메인 서비스로 추출, 정책 상수를 `MemoryRetrievalPolicy`로 통합.

## 7. 죽은 코드 제거

- `frontend/src/components/ConversationDisplay.tsx` — import만 되고 미렌더.
- `frontend/src/hooks/useLongPress.ts` — 어디서도 import 안 됨.
- `SystemPromptService.kt:165 normalizeTemplateInput()` — 호출처 없음.
- `TossPaymentsGatewayAdapter.kt` — stub 모드만 존재, 실사용 없음.
- `TtsPort.kt:30 prepare()` — 기본구현 아무도 override 안 함.

**해결 방향**: 각 항목 실사용 여부 재확인 후 제거 또는 NoOp으로 명시.

## 8. Reactive 아키텍처 위반

- `MemoryCuratorScheduler.kt:20` — `@Scheduled` 안에서 `.block()` 호출, 스케줄러 스레드 블로킹.
- `MissionApplicationService.kt:56` — `@Transactional`이 R2DBC 리액티브 환경에서 TX 전파 안 됨.
- `DialogueInputService.kt:35-63` — `.cache()` 적용이 일부 Mono에만 있음(불일치, 중복 로드 위험).

**해결 방향**: 블로킹 호출 제거(비동기 스케줄링으로 전환), `@Transactional` 제거 후 TX 경계를 infra port로 이동, 캐싱 정책 명시적 통일.

## 9. PipelineTracer 중복

- `PipelineTracer.kt:18-172` — null-check + supplier 래퍼 패턴이 8개 trace 함수(21,50,76,95,106,119,137,149)에서 반복(~50줄).

**해결 방향**: `traceIfPresent<T>(stage, supplier)` 고차함수로 통합.

## 10. ID 값객체 + Voice/Builder 보일러플레이트

- `CreditTransactionId.kt`, `ConversationSessionId.kt`, `MissionId.kt`, `UserId.kt` — 5개 타입 UUID 생성/검증 로직 반복.
- `UserCredit.kt:63-67`, `ConversationSession.kt:35-43` — data class인데 getter 수동 재정의.
- `Voice.kt:19-56` — Builder 패턴 수동 구현, `copy()`로 대체 가능.
- `CreditTransaction.kt:44-62` — `of()` 팩토리 2개 중복.
- `SignupBonus.kt:3-5` — 필드 없는 싱글톤인데 class로 정의(object 권장).

**해결 방향**: ID 공통 베이스 추출, 불필요한 getter/Builder 제거, object 전환.

## 11. Infrastructure 설정/매직넘버 산재

- Redis 키 프리픽스가 `RefreshTokenRedisAdapter.kt:21`, `RedisConversationCounterAdapter.kt:13`, `ConversationCachingAdapter.kt:180` 3곳에 하드코딩.
- `LoadBalancedSupertoneTtsAdapter.kt:35-36` — 최대 재시도 2회 매직넘버.
- `GoogleOAuthUserInfoExtractor.kt`, `KakaoOAuthUserInfoExtractor.kt`, `NaverOAuthUserInfoExtractor.kt` — 거의 동일 구조 반복.
- `TtsErrorClassifier.kt:18` — 비HTTP 에러 전부 TEMPORARY로 분류(영구 에러 오분류 위험).
- `ConversationCachingAdapter.kt:32-35`, `RefreshTokenRedisAdapter.kt:31` — Redis 어댑터마다 독립적으로 ObjectMapper 초기화.
- `SpringAiVectorDbAdapter.kt:156-159` — Qdrant payload 타입 변환 중복(Double→Float/Int/Long).
- `LlmMemoryExtractionAdapter.kt:39-80` — 시스템 프롬프트 42줄이 코드에 하드코딩.
- `TokenAwareLlmAdapter.kt:32` — `ConcurrentHashMap` TTL 없음, 장기 실행 시 메모리 누적 위험.
- `SupertoneConfig.kt:1-6` — 생성 직후 버려지는 불필요 래퍼 클래스.

**해결 방향**: `RedisKeys` enum으로 키 통합, 재시도/타임아웃 값을 `RagDialogueProperties`로 설정화, OAuth 추출기 공통 베이스 추출, ObjectMapper Bean 통일 주입, 프롬프트 파일 분리, correlationId 맵에 TTL 캐시(Caffeine) 적용.

---

## 이슈 매핑

| # | 이슈 주제 | 위 섹션 |
|---|---|---|
| 1 | Credit 로직 중복 + CreditChargingPort 이름 충돌 | §1 |
| 2 | 예외 처리 패턴 통일 | §2 |
| 3 | App.tsx God Component 분해 | §3 |
| 4 | MetricsController 책임 분리 | §4 |
| 5 | DialogueController 책임 분리 | §5 |
| 6 | Memory 도메인 서비스 분리 | §6 |
| 7 | 죽은 코드 제거 | §7 |
| 8 | Reactive 아키텍처 위반 수정 | §8 |
| 9 | PipelineTracer 중복 제거 | §9 |
| 10 | ID 값객체/Voice Builder 보일러플레이트 정리 | §10 |
| 11 | Infrastructure 설정/매직넘버 정리 | §11 |
