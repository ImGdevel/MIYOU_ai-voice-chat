# TTS API Load Balancing

## 개요

TTS API의 rate limit 및 quota 제한을 우회하기 위해 다중 API 키를 사용한 로드 밸런싱 시스템 구현.

## 로드 밸런싱 전략

### 1. Health-aware
- Circuit breaker 상태의 endpoint 자동 제외
- 5분 후 자동 복구 시도
- 10초 주기로 복구 체크 (성능 최적화)

### 2. Least-loaded
- 활성 요청 수가 가장 적은 endpoint 우선 선택
- 실시간 부하 추적 (AtomicInteger)

### 3. Round-robin
- 동일 부하일 때 순차 분배
- Lock-free 구현 (AtomicInteger index)

## 상태 관리

### Endpoint Health States
- `HEALTHY`: 정상 작동
- `RATE_LIMITED`: 429 에러 감지 시
- `QUOTA_EXCEEDED`: 토큰 소진
- `CIRCUIT_OPEN`: 장애로 5분간 사용 중지

### 자동 복구
- 요청 성공 시 HEALTHY로 자동 복구
- Circuit breaker는 5분 후 자동 재시도

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
```

## 성능 최적화

### WebClient 재사용
- Endpoint별 WebClient 인스턴스 캐싱 (ConcurrentHashMap)
- Connection pool 재사용으로 지연시간 감소

### 알고리즘 최적화
- 스트림 연산 O(3n) → 단일 루프 O(n)
- Circuit breaker 체크 주기 제한 (10초)
- 불필요한 timestamp 연산 제거

### 성능 영향
- WebClient 생성 오버헤드 제거: ~50ms → 0ms
- 로드 밸런싱 선택 시간: ~100μs → ~10μs
- 메모리: 중간 List 객체 생성 제거

## 모니터링

로그를 통해 endpoint 선택 및 상태 추적:
```
Using endpoint endpoint-2 for TTS request (active: 3)
Endpoint endpoint-1 rate limited
Recovering endpoint endpoint-1 from circuit breaker
```

## 아키텍처

- `TtsLoadBalancer`: 로드 밸런싱 로직
- `TtsEndpoint`: Endpoint 상태 관리
- `LoadBalancedSupertoneTtsAdapter`: TtsPort 구현체
- `TtsConfiguration`: Spring Bean 설정
