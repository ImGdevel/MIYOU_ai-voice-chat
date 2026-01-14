# 모니터링 & 관찰 가능성

## 모니터링 스택

```mermaid
graph LR
    App["Spring Boot App\n:8081"] -->|"/actuator/prometheus"| Prom["Prometheus\n:9090"]
    App -->|logs| Alloy["Alloy\n(수집 에이전트)"]
    Alloy --> Loki["Loki\n(로그 저장)"]
    Prom --> Grafana["Grafana\n:3000"]
    Loki --> Grafana
    Prom --> AM["Alertmanager\n:9093"]
    AM -->|webhook| Slack["Slack 알림"]
```

---

## AOP 기반 파이프라인 계측

코드 수정 없이 `@MonitoredPipeline` 어노테이션만으로 파이프라인 전체를 자동 계측합니다.

```mermaid
sequenceDiagram
    participant AOP as MonitoringAspect
    participant CTX as Reactor Context
    participant SVC as PipelineService
    participant RPT as PipelineReporter

    AOP->>CTX: PipelineTracer 주입
    CTX->>SVC: 파이프라인 실행
    SVC->>CTX: tracer.startStage("LlmStreaming")
    SVC->>CTX: tracer.endStage("LlmStreaming", tokens)
    SVC-->>AOP: 완료
    AOP->>RPT: LoggingReporter (콘솔 출력)
    AOP->>RPT: PersistentReporter (MongoDB + Prometheus)
```

### 수집 메트릭

| 메트릭 | 설명 |
|--------|------|
| `pipeline.stage.duration` | 스테이지별 처리 시간 |
| `pipeline.stage.gap` | 연속 스테이지 간 지연 |
| `pipeline.llm.tokens` | 입력/출력 토큰 수 |
| `pipeline.tts.endpoint` | TTS 엔드포인트별 성공/실패율 |
| `pipeline.memory.retrieved` | 메모리 검색 결과 수·스코어 |
| `pipeline.error.rate` | 스테이지별 오류율 |

---

## 파이프라인 타임라인 시각화 (Grafana)

```
시간 →
[Input     |████░░░░░░░░░░░░░░░░░░░░░░░░░░░] 100ms
[Prompt    |░░░░███░░░░░░░░░░░░░░░░░░░░░░░░]  15ms
[LLM       |░░░░░░░████████████████░░░░░░░░] 800ms
[TTS       |░░░░░░░░░░░░████████████████░░░] 600ms
[PostProc  |░░░░░░░░░░░░░░░░░░░░░░░░░░░████]  백그라운드
```

---

## 비용 추적

```mermaid
flowchart LR
    LLM["LLM 응답\n(스트리밍)"] -->|"마지막 청크\nstreamUsage:true"| TOKEN["실제 토큰 수\n(prompt + completion)"]
    TOKEN --> CALC["비용 계산\n× 모델 단가"]
    CALC --> STORE["MongoDB 저장"]
    STORE --> PROM["Prometheus 메트릭\ncost_per_request"]
    PROM --> GRAFANA["Grafana 대시보드\n월별 누적 비용"]
```

---

## 인프라 기동

```bash
# 모니터링 스택 전체 기동
docker-compose -f docker-compose.monitoring.yml up -d

# 접속
# Grafana    http://localhost:3000
# Prometheus http://localhost:9090
# Alertmanager http://localhost:9093
```

---

## 관련 문서

- [모니터링 통합 가이드](../monitoring/MONITORING_INTEGRATION.md)
- [Grafana 구현 계획](../monitoring/GRAFANA_IMPLEMENTATION_PLAN.md)
- [성능 측정 가이드](../performance/PERFORMANCE_MEASUREMENT_GUIDE.md)
