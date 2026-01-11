# TTS API Load Balancing

## 개요

TTS API의 rate limit 및 quota 제한을 우회하기 위해 다중 API 키를 사용한 로드 밸런싱 시스템 구현.

## 로드 밸런싱 전략

### 1. Health-aware
- 비정상 상태의 endpoint 자동 제외
- 30초 주기로 일시적 장애 복구 시도
- 10초 주기로 복구 체크 (성능 최적화)

### 2. Least-loaded
- 활성 요청 수가 가장 적은 endpoint 우선 선택
- 실시간 부하 추적 (AtomicInteger)

### 3. Round-robin
- 동일 부하일 때 순차 분배
- Lock-free 구현 (AtomicInteger index)

## 에러 분류 및 처리

### HTTP 상태 코드 기반 분류

**일시적 에러 (TEMPORARY)**
- 429 Too Many Requests (요청 제한 초과)
- 408 Request Timeout (타임아웃)
- 500+ Server Error (서버 내부 오류)
- 처리: 즉시 다른 엔드포인트로 재시도 (최대 2회)

**영구 장애 (PERMANENT)**
- 401 Unauthorized (인증 실패)
- 402 Not Enough Credits (크레딧 부족)
- 403 Forbidden (권한 없음)
- 처리: 다른 엔드포인트로 재시도 후 영구 비활성화 + 이벤트 발행

**클라이언트 에러 (CLIENT_ERROR)**
- 400 Bad Request (잘못된 요청)
- 404 Not Found (리소스 없음)
- 처리: 재시도 없이 즉시 에러 전파

### Endpoint Health States
- `HEALTHY`: 정상 작동
- `TEMPORARY_FAILURE`: 일시적 장애 (429, 408, 500 등)
- `PERMANENT_FAILURE`: 영구 장애 (401, 402, 403 등)
- `CLIENT_ERROR`: 클라이언트 에러 (400, 404 등)

## 복구 전략

### 일시적 장애 복구
- 30초 후 자동 복구 시도
- 요청 성공 시 즉시 HEALTHY 상태로 전환
- 다른 정상 엔드포인트로 즉시 요청 전환

### 영구 장애 복구
- 자동 복구 시도 없음 (수동 개입 필요)
- 장애 이벤트 발행으로 개발자에게 알림
- 다른 정상 엔드포인트로 즉시 요청 전환

### 클라이언트 에러
- 복구 시도 없음
- 에러를 호출자에게 즉시 전파

## 재시도 정책

### 자동 재시도
- 최대 재시도 횟수: 2회 (총 2번 시도)
- 일시적 에러/영구 장애: 다른 엔드포인트로 즉시 재시도
- 클라이언트 에러: 재시도 없이 즉시 실패
- 모든 엔드포인트 실패 시: "모든 TTS 엔드포인트 요청 실패" 에러 반환

### 타임아웃
- TTS 스트리밍 요청: 10초
- 엔드포인트 준비(warmup): 2초

## 장애 알림

### 영구 장애 이벤트
영구 장애(401, 402, 403) 발생 시 `TtsEndpointFailureEvent` 발행:
```java
TtsEndpointFailureEvent {
  endpointId: "endpoint-1"
  errorType: "PERMANENT_FAILURE"
  errorMessage: "[402] 크레딧 부족"
  occurredAt: 2025-01-15T10:30:00Z
}
```

현재는 System.err로 로깅되며, 향후 이벤트 드리븐 시스템(Kafka, SNS 등)과 연동 가능.

## 설정

```yaml
supertone:
  endpoints:
    - id: endpoint-1
      api-key: ${SUPERTONE_API_KEY_1}
      base-url: https://supertoneapi.com
    - id: endpoint-2
      api-key: ${SUPERTONE_API_KEY_2}
      base-url: https://supertoneapi.com
    - id: endpoint-3
      api-key: ${SUPERTONE_API_KEY_3}
      base-url: https://supertoneapi.com
```

## 성능 최적화

### WebClient 재사용
- Endpoint별 WebClient 인스턴스 캐싱 (ConcurrentHashMap)
- Connection pool 재사용으로 지연시간 감소

### 알고리즘 최적화
- 스트림 연산 O(3n) → 단일 루프 O(n)
- 복구 체크 주기 제한 (10초)
- 불필요한 timestamp 연산 제거

### 성능 영향
- WebClient 생성 오버헤드 제거: ~50ms → 0ms
- 로드 밸런싱 선택 시간: ~100μs → ~10μs
- 메모리: 중간 List 객체 생성 제거

## 모니터링

### 로그 메시지
엔드포인트 선택 및 상태 추적:
```
엔드포인트 endpoint-2 선택, 활성 요청 수: 3, 시도 횟수: 1
엔드포인트 endpoint-1 일시적 장애: [429] 요청 제한 초과
엔드포인트 endpoint-1 일시적 장애 복구 시도
엔드포인트 endpoint-2 영구 장애: [402] 크레딧 부족
엔드포인트 endpoint-3 장애로 다른 엔드포인트로 재시도 (2회차)
클라이언트 에러 발생, 재시도 없이 즉시 실패: [400] 잘못된 요청
```

### 장애 시나리오별 동작

**시나리오 1: 일시적 에러 (429)**
1. endpoint-1에서 429 에러 발생
2. endpoint-1을 TEMPORARY_FAILURE로 표시
3. endpoint-2로 즉시 재시도
4. 30초 후 endpoint-1 자동 복구 시도

**시나리오 2: 영구 장애 (402)**
1. endpoint-1에서 402 에러 발생
2. endpoint-1을 PERMANENT_FAILURE로 표시
3. 장애 이벤트 발행 (개발자 알림)
4. endpoint-2로 즉시 재시도
5. endpoint-1은 수동 복구 필요

**시나리오 3: 클라이언트 에러 (400)**
1. endpoint-1에서 400 에러 발생
2. endpoint-1을 CLIENT_ERROR로 표시
3. 재시도 없이 즉시 에러 반환

**시나리오 4: 모든 엔드포인트 다운**
1. endpoint-1 실패 → endpoint-2로 재시도
2. endpoint-2 실패 → 최대 재시도 횟수 초과
3. "모든 TTS 엔드포인트 요청 실패" 에러 반환

## 알려진 취약점 및 개선 포인트

> **분석일**: 2026-02-15
> **분석 대상**: TtsLoadBalancer, TtsEndpoint, TtsErrorClassifier, LoadBalancedSupertoneTtsAdapter

### 1. 전체 장애 시 Fallback 취약점

**위치**: [TtsLoadBalancer.java:69-71](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/loadbalancer/TtsLoadBalancer.java#L69-L71)

**문제**:
```java
if (bestEndpoint == null) {
    log.warn("모든 TTS 엔드포인트가 비정상 상태입니다. 기본 엔드포인트를 사용합니다.");
    return endpoints.get(0);  // PERMANENT_FAILURE 상태여도 반환
}
```

**영향**: 모든 엔드포인트가 비정상일 때 영구 장애 상태(401/402/403)의 엔드포인트로 요청 시도

**재현 조건**:
1. 모든 엔드포인트가 PERMANENT_FAILURE 또는 TEMPORARY_FAILURE 상태
2. `selectEndpoint()` 호출 시 무조건 첫 번째 엔드포인트 반환

**심각도**: 🟠 Medium

---

### 2. Health/CircuitOpenedAt Race Condition

**위치**: [TtsEndpoint.java:42-50](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/loadbalancer/TtsEndpoint.java#L42-L50)

**문제**:
```java
public void setHealth(EndpointHealth health) {
    this.health = health;                    // volatile 쓰기 1
    if (health == EndpointHealth.TEMPORARY_FAILURE || ...) {
        this.circuitOpenedAt = Instant.now(); // volatile 쓰기 2 (원자적이지 않음)
    }
}
```

**영향**: 멀티스레드 환경에서 `health`와 `circuitOpenedAt` 사이 불일치 가능

**재현 조건**:
1. Thread A: `setHealth(TEMPORARY_FAILURE)` 호출
2. Thread B: `setHealth(HEALTHY)` 호출 (동시에)
3. 결과: `health=HEALTHY`, `circuitOpenedAt=non-null` (불일치)

**심각도**: 🟡 Low (실제 영향 제한적)

---

### 3. CLIENT_ERROR 상태 처리 불일치

**위치**: [TtsEndpoint.java:68-69](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/loadbalancer/TtsEndpoint.java#L68-L69)

**문제**:
```java
public boolean isAvailable() {
    return health == EndpointHealth.HEALTHY;  // CLIENT_ERROR도 비정상 처리
}
```

**영향**: 400/404 에러 발생 시 해당 엔드포인트가 비정상으로 처리되어 다음 요청에서 제외됨

**논리적 문제**: CLIENT_ERROR는 클라이언트 요청 문제이지 엔드포인트 문제가 아님. 다음 정상 요청은 처리 가능해야 함.

**재현 조건**:
1. 잘못된 요청으로 400 에러 발생
2. 해당 엔드포인트가 CLIENT_ERROR 상태로 변경
3. 이후 정상 요청도 해당 엔드포인트 제외 (30초간 복구 안됨)

**심각도**: 🟠 Medium

---

### 4. 요청 취소 시 ActiveRequests 누수

**위치**: [LoadBalancedSupertoneTtsAdapter.java:58-74](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/LoadBalancedSupertoneTtsAdapter.java#L58-L74)

**문제**:
```java
endpoint.incrementActiveRequests();

return synthesizeWithEndpoint(endpoint, text, format)
    .doOnComplete(() -> endpoint.decrementActiveRequests())  // 완료 시
    .onErrorResume(error -> {
        endpoint.decrementActiveRequests();  // 에러 시
        // ...
    });
// doOnCancel() 없음!
```

**영향**: 클라이언트가 요청을 취소하면 `activeRequests` 카운트가 감소하지 않음

**재현 조건**:
1. TTS 요청 시작 (`incrementActiveRequests()`)
2. 클라이언트가 Subscription 취소
3. `decrementActiveRequests()` 호출되지 않음
4. 시간이 지나면서 카운트 누적 → 해당 엔드포인트 부하가 높게 측정됨

**심각도**: 🟠 Medium

---

### 5. Warmup 실패 시 상태 미반영

**위치**: [LoadBalancedSupertoneTtsAdapter.java:133-135](webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/dialogue/adapter/tts/LoadBalancedSupertoneTtsAdapter.java#L133-L135)

**문제**:
```java
.doOnError(error -> log.warn("앤드포인트 준비에 실패했습니다. : {}", endpoint.getId(), error))
.onErrorResume(error -> Mono.empty())  // 에러 무시, 상태 변경 없음
```

**영향**: Warmup 실패한 엔드포인트도 HEALTHY 상태로 유지되어 실제 요청에서 실패 가능

**재현 조건**:
1. 애플리케이션 시작 시 `prepare()` 호출
2. 특정 엔드포인트 네트워크 문제로 warmup 실패
3. 해당 엔드포인트는 HEALTHY 상태 유지
4. 첫 실제 요청에서 실패 후 재시도 발생

**심각도**: 🟡 Low

---

### 취약점 요약 테이블

| # | 취약점 | 심각도 | 재현 난이도 | 영향 |
|---|--------|--------|-------------|------|
| 1 | 전체 장애 시 Fallback | 🟠 Medium | 쉬움 | 영구 장애 엔드포인트로 요청 |
| 2 | Health/CircuitOpenedAt Race | 🟡 Low | 어려움 | 상태 불일치 |
| 3 | CLIENT_ERROR 처리 | 🟠 Medium | 쉬움 | 정상 엔드포인트 제외 |
| 4 | ActiveRequests 누수 | 🟠 Medium | 중간 | 부하 측정 왜곡 |
| 5 | Warmup 실패 미반영 | 🟡 Low | 쉬움 | 첫 요청 실패 |

---

## 아키텍처

### 주요 컴포넌트
- `TtsLoadBalancer`: 로드 밸런싱 및 복구 로직
- `TtsEndpoint`: Endpoint 상태 관리
- `TtsErrorClassifier`: HTTP 상태 코드 기반 에러 분류
- `TtsEndpointFailureEvent`: 영구 장애 이벤트
- `LoadBalancedSupertoneTtsAdapter`: TtsPort 구현체 (재시도 로직)
- `TtsConfiguration`: Spring Bean 설정

### 에러 처리 흐름
```
HTTP 요청 실패
    ↓
TtsErrorClassifier.classifyError()
    ↓
┌─────────────┬─────────────┬─────────────┐
│  TEMPORARY  │  PERMANENT  │CLIENT_ERROR │
└─────────────┴─────────────┴─────────────┘
      ↓              ↓              ↓
  30초 후 복구    이벤트 발행    즉시 전파
      ↓              ↓              ↓
  다른 엔드포인트로 재시도 (최대 2회)
```
