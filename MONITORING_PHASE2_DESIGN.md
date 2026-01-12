# MIYOU 모니터링 Phase 2 설계

**작성일**: 2026-02-16
**Phase**: 2 (비용 & UX 메트릭)
**상태**: 설계 단계

---

## 📋 개요

Phase 1 완료 후, Phase 2에서는 **운영 효율성**과 **사용자 경험** 중심의 메트릭을 추가합니다.

### 목표

1. **비용 최적화**: LLM/TTS 비용 추적 및 예산 관리
2. **UX 개선**: 사용자 경험 지표 모니터링
3. **운영 투명성**: 실시간 비용/성능 대시보드

---

## 🎯 Phase 2 범위

### Phase 2A: 비용 추적 (Cost Tracking)

**우선순위**: HIGH
**소요 시간**: 1-2일

#### 제공 메트릭

| 메트릭 이름 | 타입 | Tags | 설명 |
|-----------|------|------|------|
| `llm.cost.usd.total` | Counter | `model` | LLM 누적 비용 (USD) |
| `llm.cost.usd.daily` | Gauge | `model` | LLM 일일 비용 (USD) |
| `llm.cost.usd.monthly` | Gauge | `model` | LLM 월별 비용 (USD) |
| `llm.cost.by_user` | Counter | `user_id`, `model` | 사용자별 LLM 비용 |
| `tts.cost.usd.total` | Counter | `provider` | TTS 누적 비용 (USD) |
| `tts.cost.usd.daily` | Gauge | `provider` | TTS 일일 비용 (USD) |
| `tts.cost.usd.monthly` | Gauge | `provider` | TTS 월별 비용 (USD) |
| `cost.budget.remaining` | Gauge | `budget_type` | 남은 예산 (USD) |

#### 비용 계산 로직

**LLM 비용**:
```java
// GPT-4o 가격 (2026년 기준)
double promptCost = (promptTokens / 1_000_000.0) * 2.50;   // $2.50 per 1M tokens
double completionCost = (completionTokens / 1_000_000.0) * 10.00; // $10.00 per 1M tokens
double totalCost = promptCost + completionCost;
```

**TTS 비용**:
```java
// Supertone 가격 (예상)
double cost = (characters / 1_000.0) * 0.015; // $0.015 per 1K characters
```

---

### Phase 2B: UX 메트릭 (User Experience)

**우선순위**: MEDIUM
**소요 시간**: 1일

#### 제공 메트릭

| 메트릭 이름 | 타입 | Tags | 설명 |
|-----------|------|------|------|
| `ux.response.latency.first` | DistributionSummary | - | 첫 응답 시간 (TTFB) |
| `ux.response.latency.complete` | DistributionSummary | - | 전체 응답 완료 시간 |
| `ux.error.rate` | Counter | `error_type` | 사용자 에러 발생 횟수 |
| `ux.satisfaction.score` | Gauge | - | 사용자 만족도 점수 (Apdex) |
| `ux.abandonment.rate` | Counter | `stage` | 대화 중단율 |

#### UX 계산 로직

**Apdex (Application Performance Index)**:
```java
// T = 2초 (만족 기준)
double satisfied = count(latency <= 2000);
double tolerating = count(2000 < latency <= 8000);
double frustrated = count(latency > 8000);

double apdex = (satisfied + tolerating * 0.5) / total;
// 0.95 이상: Excellent
// 0.85-0.94: Good
// 0.70-0.84: Fair
// 0.50-0.69: Poor
// < 0.50: Unacceptable
```

---

## 🏗️ 구현 계획

### 파일 생성 목록

#### 1. CostTrackingMetricsConfiguration.java

**경로**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/`

**구조**:
```java
@Component
public class CostTrackingMetricsConfiguration {
    private final MeterRegistry meterRegistry;

    // LLM 비용
    private final Counter llmCostTotal;
    private final AtomicReference<Double> llmCostDaily;
    private final AtomicReference<Double> llmCostMonthly;

    // TTS 비용
    private final Counter ttsCostTotal;
    private final AtomicReference<Double> ttsCostDaily;
    private final AtomicReference<Double> ttsCostMonthly;

    // 예산 추적
    private final AtomicReference<Double> budgetRemaining;

    public CostTrackingMetricsConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Counter 등록
        this.llmCostTotal = Counter.builder("llm.cost.usd.total")
            .description("Total LLM cost in USD")
            .register(meterRegistry);

        // Gauge 등록
        this.llmCostDaily = meterRegistry.gauge(
            "llm.cost.usd.daily",
            new AtomicReference<>(0.0)
        );
    }

    public void recordLlmCost(String model, int promptTokens, int completionTokens) {
        double cost = calculateLlmCost(model, promptTokens, completionTokens);

        // 누적 비용
        llmCostTotal.increment(cost);
        Counter.builder("llm.cost.by_model")
            .tag("model", model)
            .register(meterRegistry)
            .increment(cost);

        // 일일 비용 업데이트
        llmCostDaily.updateAndGet(current -> current + cost);
    }

    public void recordUserLlmCost(String userId, String model, double cost) {
        Counter.builder("llm.cost.by_user")
            .tag("user_id", userId)
            .tag("model", model)
            .register(meterRegistry)
            .increment(cost);
    }

    public void recordTtsCost(String provider, int characters) {
        double cost = calculateTtsCost(provider, characters);

        ttsCostTotal.increment(cost);
        ttsCostDaily.updateAndGet(current -> current + cost);
    }

    public void resetDailyCost() {
        llmCostDaily.set(0.0);
        ttsCostDaily.set(0.0);
    }

    public void updateBudgetRemaining(double remaining) {
        budgetRemaining.set(remaining);
    }

    private double calculateLlmCost(String model, int promptTokens, int completionTokens) {
        // GPT-4o 기준
        if (model.contains("gpt-4o")) {
            double promptCost = (promptTokens / 1_000_000.0) * 2.50;
            double completionCost = (completionTokens / 1_000_000.0) * 10.00;
            return promptCost + completionCost;
        }

        // GPT-4o-mini 기준
        if (model.contains("gpt-4o-mini")) {
            double promptCost = (promptTokens / 1_000_000.0) * 0.150;
            double completionCost = (completionTokens / 1_000_000.0) * 0.600;
            return promptCost + completionCost;
        }

        return 0.0;
    }

    private double calculateTtsCost(String provider, int characters) {
        // Supertone 기준 (예상)
        if (provider.contains("supertone")) {
            return (characters / 1_000.0) * 0.015;
        }
        return 0.0;
    }
}
```

#### 2. UxMetricsConfiguration.java

**경로**: `webflux-dialogue/src/main/java/com/study/webflux/rag/infrastructure/monitoring/config/`

**구조**:
```java
@Component
public class UxMetricsConfiguration {
    private final MeterRegistry meterRegistry;

    // 응답 시간
    private final DistributionSummary firstResponseLatency;
    private final DistributionSummary completeResponseLatency;

    // 에러율
    private final Counter errorRate;

    // 만족도
    private final AtomicReference<Double> satisfactionScore;

    // 중단율
    private final Counter abandonmentRate;

    public UxMetricsConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.firstResponseLatency = DistributionSummary.builder("ux.response.latency.first")
            .description("Time to first response token (TTFB) in milliseconds")
            .baseUnit("ms")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry);

        this.completeResponseLatency = DistributionSummary.builder("ux.response.latency.complete")
            .description("Complete response time in milliseconds")
            .baseUnit("ms")
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .register(meterRegistry);

        this.errorRate = Counter.builder("ux.error.rate")
            .description("User-facing error count")
            .register(meterRegistry);

        this.satisfactionScore = meterRegistry.gauge(
            "ux.satisfaction.score",
            new AtomicReference<>(1.0)
        );
    }

    public void recordFirstResponseLatency(long latencyMs) {
        firstResponseLatency.record(latencyMs);
        updateSatisfactionScore(latencyMs);
    }

    public void recordCompleteResponseLatency(long latencyMs) {
        completeResponseLatency.record(latencyMs);
    }

    public void recordError(String errorType) {
        errorRate.increment();
        Counter.builder("ux.error.by_type")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment();
    }

    public void recordAbandonment(String stage) {
        abandonmentRate.increment();
        Counter.builder("ux.abandonment.by_stage")
            .tag("stage", stage)
            .register(meterRegistry)
            .increment();
    }

    private void updateSatisfactionScore(long latencyMs) {
        // Apdex 계산 (T = 2000ms)
        // Satisfied: <= 2000ms
        // Tolerating: 2000ms < x <= 8000ms
        // Frustrated: > 8000ms

        double score;
        if (latencyMs <= 2000) {
            score = 1.0; // Satisfied
        } else if (latencyMs <= 8000) {
            score = 0.5; // Tolerating
        } else {
            score = 0.0; // Frustrated
        }

        // Moving average (간단한 구현, 실제로는 더 정교한 계산 필요)
        satisfactionScore.updateAndGet(current ->
            current * 0.9 + score * 0.1
        );
    }
}
```

---

## 🔌 서비스 통합 계획

### 1. MicrometerPipelineMetricsReporter 확장

```java
public class MicrometerPipelineMetricsReporter implements PipelineMetricsReporter {
    private final CostTrackingMetricsConfiguration costMetrics;
    private final UxMetricsConfiguration uxMetrics;

    @Override
    public void recordSummary(PipelineSummary summary) {
        // 기존 메트릭 기록
        recordBasicMetrics(summary);
        recordStageGapMetrics(summary);
        recordRagQualityMetrics(summary);

        // Phase 2: 비용 메트릭 기록
        recordCostMetrics(summary);

        // Phase 2: UX 메트릭 기록
        recordUxMetrics(summary);
    }

    private void recordCostMetrics(PipelineSummary summary) {
        for (StageSnapshot stage : summary.stages()) {
            if (stage.stage() == DialoguePipelineStage.LLM_COMPLETION) {
                String model = stage.attributes().getOrDefault("model", "unknown").toString();

                Object promptTokensObj = stage.attributes().get("prompt.tokens");
                Object completionTokensObj = stage.attributes().get("completion.tokens");

                if (promptTokensObj instanceof Number && completionTokensObj instanceof Number) {
                    int promptTokens = ((Number) promptTokensObj).intValue();
                    int completionTokens = ((Number) completionTokensObj).intValue();

                    costMetrics.recordLlmCost(model, promptTokens, completionTokens);

                    // 사용자별 비용 (userId가 attributes에 있다면)
                    Object userIdObj = stage.attributes().get("user.id");
                    if (userIdObj != null) {
                        String userId = userIdObj.toString();
                        double cost = calculateLlmCost(model, promptTokens, completionTokens);
                        costMetrics.recordUserLlmCost(userId, model, cost);
                    }
                }
            }

            if (stage.stage() == DialoguePipelineStage.TTS_SYNTHESIS) {
                String provider = stage.attributes().getOrDefault("provider", "unknown").toString();
                Object charactersObj = stage.attributes().get("characters");

                if (charactersObj instanceof Number) {
                    int characters = ((Number) charactersObj).intValue();
                    costMetrics.recordTtsCost(provider, characters);
                }
            }
        }
    }

    private void recordUxMetrics(PipelineSummary summary) {
        // 첫 응답 시간 (pipeline.response.first)
        Long firstResponseTimeMs = summary.firstResponseTimeMillis();
        if (firstResponseTimeMs != null && firstResponseTimeMs >= 0) {
            uxMetrics.recordFirstResponseLatency(firstResponseTimeMs);
        }

        // 전체 응답 시간 (pipeline.duration)
        long totalDurationMs = summary.endedAt().toEpochMilli() - summary.startedAt().toEpochMilli();
        uxMetrics.recordCompleteResponseLatency(totalDurationMs);

        // 에러 기록
        if (summary.status() == PipelineStatus.FAILED) {
            String errorType = summary.attributes().getOrDefault("error.type", "unknown").toString();
            uxMetrics.recordError(errorType);
        }
    }
}
```

### 2. DialogueController 통합 (중단율 추적)

```java
@RestController
public class DialogueController {
    private final UxMetricsConfiguration uxMetrics;

    @PostMapping("/rag/dialogue/stream")
    public Flux<ServerSentEvent<String>> streamDialogue(@RequestBody DialogueRequest request) {
        return dialoguePipelineService.execute(request)
            .doOnCancel(() -> {
                // 사용자가 중단한 경우
                uxMetrics.recordAbandonment("streaming");
            })
            .doOnError(error -> {
                // 에러로 인한 중단
                uxMetrics.recordAbandonment("error");
            });
    }
}
```

### 3. 일일 비용 리셋 스케줄러

```java
@Component
public class DailyCostResetScheduler {
    private final CostTrackingMetricsConfiguration costMetrics;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void resetDailyCost() {
        costMetrics.resetDailyCost();
    }
}
```

---

## 📊 Grafana 대시보드 설계

### miyou-cost-tracking.json

**구성**: 4 Rows, 10 Panels

**Row 1: 비용 KPI (4 panels)**
1. 오늘 총 비용 (Stat) - `llm.cost.usd.daily + tts.cost.usd.daily`
2. 이번 달 총 비용 (Stat) - `llm.cost.usd.monthly + tts.cost.usd.monthly`
3. 남은 예산 (Gauge) - `cost.budget.remaining`
4. 일일 평균 비용 (Stat) - `llm.cost.usd.monthly / day_of_month`

**Row 2: LLM 비용 분석 (3 panels)**
5. 모델별 비용 분포 (Pie Chart) - `sum(llm.cost.by_model) by (model)`
6. 시간대별 LLM 비용 (Time Series) - `rate(llm.cost.usd.total[5m])`
7. 토큰당 비용 효율 (Stat) - `llm.cost.usd.total / llm.tokens.total`

**Row 3: TTS 비용 분석 (2 panels)**
8. TTS 비용 추이 (Time Series) - `rate(tts.cost.usd.total[5m])`
9. 문자당 비용 (Stat) - `tts.cost.usd.total / tts.characters.total`

**Row 4: 사용자별 비용 (1 panel)**
10. 사용자별 비용 Top 10 (Table) - `topk(10, sum(llm.cost.by_user) by (user_id))`

---

### miyou-ux.json

**구성**: 3 Rows, 7 Panels

**Row 1: UX KPI (3 panels)**
1. 평균 TTFB (Stat) - `ux.response.latency.first{quantile="0.5"}`
2. Apdex 점수 (Gauge) - `ux.satisfaction.score`
3. 에러율 (Stat) - `rate(ux.error.rate[5m])`

**Row 2: 응답 시간 분석 (3 panels)**
4. TTFB 분포 (Time Series) - p50, p90, p95, p99
5. 전체 응답 시간 분포 (Time Series) - p50, p90, p95, p99
6. 응답 시간 히트맵 (Heatmap) - `ux.response.latency.complete`

**Row 3: 에러 & 중단 분석 (1 panel)**
7. 에러 타입별 분포 (Bar Chart) - `sum(ux.error.by_type) by (error_type)`

---

## 🔔 알림 규칙 (Alerting)

### 비용 알림

```yaml
# 일일 비용 초과
- alert: DailyCostExceeded
  expr: llm_cost_usd_daily + tts_cost_usd_daily > 100
  for: 5m
  annotations:
    summary: "일일 비용이 $100을 초과했습니다"

# 월별 예산 90% 도달
- alert: MonthlyBudget90Percent
  expr: cost_budget_remaining < 100
  for: 5m
  annotations:
    summary: "월별 예산의 90%를 사용했습니다"
```

### UX 알림

```yaml
# Apdex 점수 낮음
- alert: LowApdexScore
  expr: ux_satisfaction_score < 0.7
  for: 10m
  annotations:
    summary: "사용자 만족도가 낮습니다 (Apdex < 0.7)"

# 에러율 높음
- alert: HighErrorRate
  expr: rate(ux_error_rate[5m]) > 0.05
  for: 5m
  annotations:
    summary: "에러율이 5%를 초과했습니다"
```

---

## ✅ 체크리스트

### 구현 단계

- [ ] CostTrackingMetricsConfiguration.java 생성
- [ ] UxMetricsConfiguration.java 생성
- [ ] MicrometerPipelineMetricsReporter 확장
- [ ] DialogueController 중단율 통합
- [ ] DailyCostResetScheduler 생성
- [ ] miyou-cost-tracking.json 대시보드 생성
- [ ] miyou-ux.json 대시보드 생성
- [ ] 알림 규칙 설정
- [ ] 문서 업데이트

### 검증 단계

- [ ] 메트릭 노출 확인 (/actuator/prometheus)
- [ ] 비용 계산 정확성 검증
- [ ] Apdex 점수 계산 검증
- [ ] 대시보드 Import
- [ ] 알림 테스트

---

**다음 단계**: Phase 2 구현 시작
