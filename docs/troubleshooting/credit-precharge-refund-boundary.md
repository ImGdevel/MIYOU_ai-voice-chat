# 크레딧 선차감 환불 경계 오류

- **발견일**: 2026-06-04
- **수정 브랜치**: `feat/credit-precharge-animation`
- **수정 커밋**: (커밋 후 기재)
- **영향 범위**: 대화 파이프라인 전체 (`executeAudioStreaming`, `executeTextOnly`)

---

## 문제 요약

선차감(precharge) 후 환불(refund) 트리거 범위가 잘못 설정되어 두 가지 독립된 문제가 존재했다.

1. **postProcessing이 크레딧 정책 범위 안에 포함됨** — 사용자가 응답을 이미 받았음에도 대화 저장 실패 시 크레딧이 환불되었다.
2. **사용자 직접 취소가 서비스 장애와 동일하게 취급됨** — 클라이언트가 연결을 끊으면 크레딧이 환불되었다.

---

## 배경

MIYOU 대화 파이프라인은 `Flux.usingWhen`을 사용해 선차감을 구현한다.

```
선차감(deduct) → LLM 스트림 → TTS 스트림 → postProcessing
  └─ 오류 발생 시 → 환불(refund)
  └─ 취소 발생 시 → 환불(refund)  ← 잘못된 설계
```

`usingWhen`의 **error** 핸들러와 **cancel** 핸들러 모두 `refundConversation()`을 호출하고 있었다.

---

## 문제 1: postProcessing 실패 시 잘못된 환불

### 원인

```kotlin
// 수정 전
private fun <T> prechargeConversation(session, stream): Flux<T> =
    Flux.usingWhen(
        resource  = deductForConversation(),
        use       = appendPostProcessing(responseStream, postProcessing), // ← 문제
        complete  = Mono.empty(),
        error     = refundConversation(),  // postProcessing 실패도 여기 진입
        cancel    = refundConversation(),
    )

private fun <T> appendPostProcessing(mainStream, postProcessing): Flux<T> =
    mainStream.concatWith(postProcessing.thenMany(Flux.empty()))
//             ↑ postProcessing이 usingWhen의 use 블록 안으로 들어감
```

`concatWith`로 붙인 `postProcessing`이 `usingWhen`의 `use` 람다 안에 존재하므로, postProcessing(대화 저장, 메모리 추출)에서 예외가 발생하면 `error` 핸들러가 발동해 환불이 실행된다.

### 재현 시나리오

```
1. 사용자 대화 요청
2. LLM 응답 생성 완료 → 클라이언트에 전달 완료
3. TTS 스트리밍 완료
4. postProcessing 시작: MongoDB에 대화 저장 중
5. MongoDB 장애로 저장 실패 → RuntimeException
6. usingWhen error 핸들러 발동
7. refundForConversation() 호출
8. 사용자는 완전한 응답을 받았으나 크레딧은 환불됨
```

### 영향

- 운영 중 MongoDB 일시 장애 시 해당 구간의 모든 대화 크레딧이 환불됨
- 크레딧이 결제 자산과 연결된 경우 실제 손실 발생
- 대화는 저장되지 않았으나 크레딧은 복구되므로 회계 불일치

---

## 문제 2: 사용자 취소 시 잘못된 환불

### 원인

`Flux.usingWhen`의 cancel 핸들러에 `refundConversation()`이 연결되어 있었다.

```kotlin
// 수정 전
{ _: CreditTransaction ->
    refundConversation(
        session,
        CancellationException("conversation stream was cancelled"),
    )
}
```

HTTP SSE/스트리밍 환경에서 cancel 시그널의 발생 원인은 사실상 하나다.

| 시그널 | 원인 |
|--------|------|
| `error` | 서버 내부 예외 (LLM 실패, TTS 실패, 네트워크 오류 등) |
| `cancel` | 구독자가 구독 취소 → 실질적으로 클라이언트 연결 종료 |

서버 측 장애는 예외를 발생시키므로 `error` 시그널로 전달된다. `cancel`은 클라이언트가 연결을 끊었을 때 Spring WebFlux가 리액티브 체인에 전파하는 시그널이다.

### 재현 시나리오

```
1. 사용자 대화 요청 → 선차감 실행
2. LLM 토큰 스트리밍 시작 → 클라이언트에 일부 전달
3. 사용자가 브라우저를 닫거나 대화 중단
4. HTTP 연결 종료 → Spring WebFlux가 cancel 전파
5. usingWhen cancel 핸들러 발동
6. refundForConversation() 호출
7. LLM과 TTS 비용은 이미 발생했으나 크레딧은 환불됨
```

### 영향

- 사용자가 의도적으로 대화를 중단해도 크레딧이 반환됨
- LLM API 호출 비용(토큰)과 TTS 합성 비용은 이미 외부에서 청구됨
- 악의적 사용자가 반복 취소로 크레딧 비용 없이 서비스 소모 가능

---

## 수정 내용

### DialoguePipelineService 변경

**핵심 원칙**:

> 크레딧 환불 트리거 범위는 "응답 스트림(LLM + TTS)"으로 한정한다.  
> postProcessing은 사용자 서비스 가치 전달 이후의 내부 작업이므로 크레딧 정책 밖에서 실행한다.  
> 사용자 직접 취소는 서비스가 감수하는 것이 아니라 사용자의 선택이므로 차감을 유지한다.

```kotlin
// 수정 후: executeAudioStreaming
return prechargeConversation(session, audioStream)          // ← 응답 스트림만 포함
    .concatWith(postProcessing.thenMany(Flux.empty()))      // ← 크레딧 범위 밖

// 수정 후: executeTextOnly
return prechargeConversation(session, textStream)           // ← 응답 스트림만 포함
    .concatWith(postProcessing.thenMany(Flux.empty()))      // ← 크레딧 범위 밖
```

```kotlin
// 수정 후: prechargeConversation
Flux.usingWhen(
    resource  = deductForConversation(),
    use       = responseStream,                 // postProcessing 제거
    complete  = Mono.empty(),                   // 정상 완료: 차감 유지
    error     = refundConversation(cause),      // 서비스 실패: 환불
    cancel    = logUserCancellation(),          // 사용자 취소: 차감 유지, 로그만
)
```

### 변경 전후 동작 비교

| 시나리오 | 변경 전 | 변경 후 |
|----------|---------|---------|
| LLM/TTS 실패 | 환불 ✓ | 환불 ✓ |
| 정상 완료 | 차감 유지 ✓ | 차감 유지 ✓ |
| postProcessing(대화 저장) 실패 | 환불 ✗ | 차감 유지 ✓ |
| 사용자 직접 취소 | 환불 ✗ | 차감 유지 ✓ |

---

## 추가된 테스트

`DialoguePipelineServiceTest`에 다음 케이스가 추가되었다.

| 테스트 | 검증 내용 |
|--------|-----------|
| `executeTextOnly_doesNotRefundWhenPostProcessingFails` | postProcessing 실패 시 `refundForConversation` 미호출 |
| `executeAudioStreaming_doesNotRefundWhenPostProcessingFails` | 오디오 파이프라인에서도 동일 |
| `executeTextOnly_doesNotRefundOnUserCancellation` | `thenCancel()`로 구독 취소 시 환불 미호출 |
| `executeAudioStreaming_refundsOnServiceFailure` | TTS 실패 시 환불 호출 확인 |

---

## 남은 과제

이번 수정에서 다루지 않은 항목이다. 필요성과 우선순위를 별도로 판단해야 한다.

### 환불 실패 복구 수단 없음

환불 자체가 실패할 경우 현재는 `logger.error`만 기록하고 종료된다.

```kotlin
.onErrorResume { refundError ->
    logger.error(...)
    Mono.empty()  // 크레딧 영구 소멸
}
```

발생 조건: 환불 시도 중 MongoDB 장애. 이미 대화 파이프라인이 에러 상태인 시점에 DB도 죽은 경우이므로 빈도는 낮다. 그러나 로그 외 복구 수단이 없으므로 운영자가 수동 조치해야 한다.

개선 방향: 실패한 환불 요청을 별도 컬렉션에 기록하고 배치 복구 처리.

### 크레딧 저장과 트랜잭션 로그의 비원자성

```kotlin
userCreditRepository.save(updated)
    .flatMap { creditTransactionRepository.save(tx) }  // 두 저장이 분리됨
```

첫 번째 저장 성공 후 두 번째에서 실패하면 잔액은 변경됐으나 로그가 없는 상태가 된다. MongoDB replica set 환경이므로 multi-document transaction 적용이 기술적으로 가능하다.
