# 2026-07-08 ErrorCode/HttpStatus 분리 설계 — ErrorCategory 도입

관련 PR: [#111](https://github.com/ImGdevel/MIYOU_ai-voice-chat/pull/111)
선행 문서: [2026-07-08_1432_domain-validation-error-boundary.md](2026-07-08_1432_domain-validation-error-boundary.md)

## 1. 배경 — 위반 발견

커밋 `025e0be`(♻️ refactor(exception): 예외 클래스를 도메인 하위 exception 패키지로 이전하고 BusinessException 기반 리팩토링)에서 `ErrorCode` 인터페이스가 `HttpStatus`(Spring Web)를 직접 프로퍼티로 들게 됐다.

```kotlin
// 변경 전 ErrorCode.kt
interface ErrorCode {
    val code: String
    val httpStatus: HttpStatus   // domain 모듈이 Spring을 알게 됨
    val message: String
}
```

`CreditErrorCode`/`DialogueErrorCode`/`MissionErrorCode` 전부 `backend/domain` 모듈 소속인데 이 인터페이스를 구현하면서, `backend/domain/build.gradle`에 `implementation 'org.springframework:spring-web'`가 직접 추가됐다. 이 레포는 "domain 계층은 순수 Java/Kotlin, 프레임워크 독립적이어야 한다"는 원칙을 `HexagonalArchitectureTest.domainShouldNotDependOnSpringOrMongoOrMicrometer()`로 강제하고 있는데, 이 커밋은 그 테스트에 `resideOutsideOfPackage("com.miyou.app.domain..exception..")` 예외 조항을 추가해 위반을 가려주는 방식으로 통과시켰다. 원칙을 지킨 게 아니라 가드레일에 구멍을 낸 것.

## 2. 검토한 대안

| 안 | 핵심 | 문제/트레이드오프 |
|---|---|---|
| A. 소수 고정 카테고리 + 세부 코드 (Google/Stripe식) | `ErrorCategory`(5~7개 고정) → `HttpStatus` 매핑은 api 모듈, `ErrorCode`는 category만 참조 | 카테고리 설계를 처음에 잘 해야 함. 최초 도입 비용 있음 |
| B. 마커 인터페이스 + 코드별 매핑 레지스트리 (DDD/헥사고날 표준) | `ErrorCode`는 code만, `code -> HttpStatus` 1:1 매핑 테이블을 api 모듈이 소유 | 원칙적으론 A보다 순수하지만 코드 하나 늘 때마다 매핑도 하나씩 늘어 유지비 더 큼 |
| C. Either/Result 기반 (Arrow-kt 등) | 예상된 실패는 예외 아니라 반환값 | 이미 `@ExceptionHandler` 기반으로 굳어진 코드베이스에 절반만 적용하면 일관성 깨짐. 이번 스코프 아님 |
| D. ErrorCode에 HttpStatus 유지 (현행 유지) | 매핑 파일 하나 안 늘어도 됨 | "domain 순수 Java" 규칙과 공존 불가 — 이미 기존 결정 위반 상태라 선택지에서 제외 |

**A안 채택.** 이유: B안 대비 매핑 테이블이 카테고리 단위(5~7개, 거의 안 늘어남)라 "새 코드 추가 시 매핑 깜빡함 → 조용히 500" 리스크가 구조적으로 낮다. 게다가 Kotlin `when`을 카테고리 enum에 대해 exhaustive하게 작성하면 매핑 누락이 컴파일 에러로 드러나 런타임 리스크가 사실상 0에 수렴한다.

## 3. 참조 사례 (실제 fetch로 확인함, 2026-07-08 기준 유효)

### 3.1 Google API 설계 가이드 — AIP-193 (Errors)

- <https://google.aip.dev/193> (구 URL `https://cloud.google.com/apis/design/errors`는 이 문서로 301 리다이렉트됨, 확인 완료)
- 핵심 모델: 서비스는 `google.rpc.Status` 메시지를 반환해야 하고, 반드시 `google.rpc.Code`(약 20개 고정 값)에 정의된 **canonical 상태 코드**를 써야 한다. `NOT_FOUND`(5, HTTP 404), `PERMISSION_DENIED`(HTTP 403), `RESOURCE_EXHAUSTED`(HTTP 429), `INVALID_ARGUMENT`, `FAILED_PRECONDITION` 등.
- 비즈니스 세부 식별은 별도 계층인 `ErrorInfo`(`reason`: `CPU_AVAILABILITY` 같은 snake_case 식별자, `domain`: 서비스명, `metadata`: 키-값)가 담당 — canonical 코드와 세부 사유가 명확히 분리되어 있음.
- `google.rpc.Code` proto 정의: <https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto>

### 3.2 Stripe API 에러 오브젝트

- <https://docs.stripe.com/api/errors> (fetch로 필드 확인 완료)
- `type`(고정 enum: `api_error`/`card_error`/`idempotency_error`/`invalid_request_error`) + `code`(세부 식별자, 프로그램적으로 분기 가능한 문자열) 이중 구조.
- `type`이 이 레포의 `ErrorCategory`, `code`가 `ErrorCode.code`(`INSUFFICIENT_CREDIT` 등)에 대응.

이 두 사례가 "카테고리(소수 고정, 전송계층 매핑용)"와 "세부 코드(비즈니스 식별, 무한히 늘어남)"를 분리하는 게 임의 설계가 아니라 대형 공개 API에서 검증된 패턴임을 뒷받침한다.

## 4. 설계

```kotlin
// backend/exception 모듈 - 순수, Spring 모름
enum class ErrorCategory {
    INVALID_INPUT, UNAUTHORIZED, PAYMENT_REQUIRED, NOT_FOUND, CONFLICT, PAYLOAD_TOO_LARGE, INTERNAL,
}

interface ErrorCode {
    val code: String
    val category: ErrorCategory
    val message: String
}
```

```kotlin
// backend/api 모듈 - Spring/HTTP 아는 유일한 지점
object ErrorCategoryHttpStatusMapping {
    fun resolve(category: ErrorCategory): HttpStatus =
        when (category) {   // exhaustive - 카테고리 추가하고 여기 안 채우면 컴파일 에러
            ErrorCategory.INVALID_INPUT -> HttpStatus.BAD_REQUEST
            ErrorCategory.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorCategory.PAYMENT_REQUIRED -> HttpStatus.PAYMENT_REQUIRED
            ErrorCategory.NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCategory.CONFLICT -> HttpStatus.CONFLICT
            ErrorCategory.PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE
            ErrorCategory.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
        }
}
```

기존 `CommonErrorCode`/`CreditErrorCode`/`DialogueErrorCode`/`MissionErrorCode`의 25개 코드 전부, 기존에 매핑되던 HTTP status와 정확히 동일한 status가 나오도록 category를 배정했다 (동작 변화 없음, 순수 내부 구조 개선).

## 5. 부수 발견 — GlobalExceptionHandler 중복 버그

마이그레이션 중 `GlobalExceptionHandler`에 구체 예외 타입별 핸들러 10개가 `BusinessException` 통합 핸들러와 중복 로직으로 남아있던 걸 발견. 일부(`InsufficientCreditException` 등)는 전용 핸들러가 `ErrorResponse`의 `details`/`path` 필드를 응답에서 누락시키고 있었다 — `BusinessException`에 채워둔 `details` 맵이 API 응답에 실제로는 안 나가는 버그. 이번 PR에서 10개 핸들러를 삭제하고 통합 핸들러 하나로 정리하면서 같이 고쳤다. 5xx는 스택트레이스 포함 ERROR 로그, 4xx는 WARN 로그로 resolve된 HttpStatus 기준 자동 분기하도록 함.

## 6. 완료 정의

- [x] `ErrorCode.httpStatus` 제거, `ErrorCategory` 도입
- [x] `CommonErrorCode`/`CreditErrorCode`/`DialogueErrorCode`/`MissionErrorCode` category로 전환 (기존 매핑 동일하게 유지)
- [x] `ErrorCategoryHttpStatusMapping` (api 모듈) 추가
- [x] `backend/domain`, `backend/exception` build.gradle에서 `spring-web` 의존성 제거
- [x] `HexagonalArchitectureTest`의 exception 패키지 예외 조항 삭제
- [x] `OpenAiWhisperSttAdapter`의 `.httpStatus` 참조 수정
- [x] `GlobalExceptionHandler` 중복 핸들러 정리 + details/path 누락 버그 수정
- [x] `./gradlew compileKotlin compileTestKotlin`, `./gradlew test` 통과 확인
- [ ] PR #111 리뷰 반영
