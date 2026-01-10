# H2. Rate Limiting 미구현 트러블 슈팅

## 1. 문제상황
- `/rag/dialogue/audio`, `/rag/dialogue/text` 엔드포인트에 초당 다량 요청이 들어오면 비용과 리소스가 급격히 증가한다.
- 단기간에 API 비용 폭증, 응답 지연, 서비스 불안정이 발생할 수 있다.

## 2. 원인
- 애플리케이션 레벨에서 요청 수를 제한하는 rate limiting 정책이 없다.
- 인증/인가가 없거나 약한 경우 외부에서 무제한 호출이 가능하다.

## 3. 대응 방안
1) Resilience4j RateLimiter 적용
- 장점: 설정이 간단하고 Spring 생태계와 궁합이 좋음
- 단점: 인스턴스 단위 제한이므로 다중 인스턴스 환경에서 분산 제한이 필요함

2) Redis 기반 분산 Rate Limiting
- 장점: 여러 인스턴스에서 일관된 제한 가능
- 단점: Redis 의존성 및 운영 복잡도 증가

3) API Gateway/Ingress 레벨 제한
- 장점: 애플리케이션 코드 변경 최소화
- 단점: 게이트웨이 설정/운영 필요, 세밀한 비즈니스 정책 적용이 제한적일 수 있음

## 4. 해결
- 1차 대응으로 Resilience4j RateLimiter를 적용해 엔드포인트 호출량을 제한한다.
- 운영 환경이 다중 인스턴스라면 Redis 기반 분산 제한으로 확장한다.

예시 (Resilience4j):
```java
@RateLimiter(name = "dialogueApi")
@PostMapping(path = "/audio")
public Flux<byte[]> ragDialogueAudio(...) {
    // ...
}
```

```yaml
resilience4j.ratelimiter:
  instances:
    dialogueApi:
      limit-for-period: 10
      limit-refresh-period: 1s
      timeout-duration: 0s
```

## 5. 알게된 점
- Rate limiting은 비용 폭탄과 서비스 불안정의 1차 방어선이다.
- 단일 인스턴스 기준 제한만으로는 분산 환경에서 충분하지 않다.
- 엔드포인트별로 트래픽 특성을 고려한 제한값 튜닝이 필요하다.

## 6. 참고
- Resilience4j RateLimiter 공식 문서
- Spring WebFlux Rate Limiting 적용 사례
- API Gateway/Ingress Rate Limiting 설정 가이드
