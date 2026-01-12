package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 사용자 경험(UX) 메트릭 설정.
 *
 * <p>
 * 제공 메트릭:
 * <ul>
 * <li>ux.response.latency.first - 첫 응답 시간 (TTFB, Time To First Byte)</li>
 * <li>ux.response.latency.complete - 전체 응답 완료 시간</li>
 * <li>ux.error.rate - 사용자 에러 발생 횟수</li>
 * <li>ux.error.by_type - 에러 타입별 발생 횟수 (tag: error_type)</li>
 * <li>ux.satisfaction.score - 사용자 만족도 점수 (Apdex)</li>
 * <li>ux.abandonment.rate - 대화 중단율</li>
 * <li>ux.abandonment.by_stage - Stage별 중단율 (tag: stage)</li>
 * </ul>
 *
 * <p>
 * Apdex (Application Performance Index):
 *
 * <pre>
 * - Satisfied: <= 2000ms (점수 1.0)
 * - Tolerating: 2000ms < x <= 8000ms (점수 0.5)
 * - Frustrated: > 8000ms (점수 0.0)
 * - Apdex = (Satisfied + Tolerating * 0.5) / Total
 * </pre>
 *
 * <p>
 * 사용 예시:
 *
 * <pre>{@code
 * uxMetrics.recordFirstResponseLatency(1500); // 1.5초 TTFB
 * uxMetrics.recordCompleteResponseLatency(5000); // 5초 완료
 * uxMetrics.recordError("timeout");
 * uxMetrics.recordAbandonment("streaming");
 * }</pre>
 */
@Component
public class UxMetricsConfiguration {

	private final MeterRegistry meterRegistry;

	// 응답 시간
	private final DistributionSummary firstResponseLatency;
	private final DistributionSummary completeResponseLatency;

	// 에러율
	private final Counter errorRate;

	// 만족도 (Apdex)
	private final AtomicReference<Double> satisfactionScore;

	// 중단율
	private final Counter abandonmentRate;

	// Apdex 계산용 카운터
	private long totalRequests = 0;
	private long satisfiedRequests = 0;
	private long toleratingRequests = 0;

	public UxMetricsConfiguration(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		// 첫 응답 시간 (TTFB)
		this.firstResponseLatency = DistributionSummary.builder("ux.response.latency.first")
			.description("Time to first response token (TTFB) in milliseconds")
			.baseUnit("ms")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// 전체 응답 시간
		this.completeResponseLatency = DistributionSummary.builder("ux.response.latency.complete")
			.description("Complete response time in milliseconds")
			.baseUnit("ms")
			.publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
			.register(meterRegistry);

		// 에러율
		this.errorRate = Counter.builder("ux.error.rate")
			.description("User-facing error count")
			.register(meterRegistry);

		// 만족도 점수 (Apdex)
		this.satisfactionScore = new AtomicReference<>(1.0);
		Gauge.builder("ux.satisfaction.score", satisfactionScore, AtomicReference::get)
			.description("User satisfaction score (Apdex)")
			.register(meterRegistry);

		// 중단율
		this.abandonmentRate = Counter.builder("ux.abandonment.rate")
			.description("Conversation abandonment count")
			.register(meterRegistry);
	}

	/**
	 * 첫 응답 시간 기록 (TTFB).
	 *
	 * @param latencyMs
	 *            응답 시간 (밀리초)
	 */
	public void recordFirstResponseLatency(long latencyMs) {
		firstResponseLatency.record(latencyMs);
		updateSatisfactionScore(latencyMs);
	}

	/**
	 * 전체 응답 완료 시간 기록.
	 *
	 * @param latencyMs
	 *            응답 시간 (밀리초)
	 */
	public void recordCompleteResponseLatency(long latencyMs) {
		completeResponseLatency.record(latencyMs);
	}

	/**
	 * 에러 기록.
	 *
	 * @param errorType
	 *            에러 타입 (예: "timeout", "llm_failure", "validation_error")
	 */
	public void recordError(String errorType) {
		errorRate.increment();

		Counter.builder("ux.error.by_type")
			.description("User-facing error count by type")
			.tag("error_type", errorType)
			.register(meterRegistry)
			.increment();
	}

	/**
	 * 대화 중단 기록.
	 *
	 * @param stage
	 *            중단 발생 Stage (예: "streaming", "error", "timeout")
	 */
	public void recordAbandonment(String stage) {
		abandonmentRate.increment();

		Counter.builder("ux.abandonment.by_stage")
			.description("Conversation abandonment count by stage")
			.tag("stage", stage)
			.register(meterRegistry)
			.increment();
	}

	/**
	 * Apdex 만족도 점수 업데이트.
	 *
	 * <p>
	 * Apdex (Application Performance Index):
	 * <ul>
	 * <li>Satisfied: <= 2000ms (점수 1.0)</li>
	 * <li>Tolerating: 2000ms < x <= 8000ms (점수 0.5)</li>
	 * <li>Frustrated: > 8000ms (점수 0.0)</li>
	 * </ul>
	 *
	 * @param latencyMs
	 *            응답 시간 (밀리초)
	 */
	private void updateSatisfactionScore(long latencyMs) {
		totalRequests++;

		if (latencyMs <= 2000) {
			// Satisfied
			satisfiedRequests++;
		} else if (latencyMs <= 8000) {
			// Tolerating
			toleratingRequests++;
		}
		// Frustrated는 카운트하지 않음 (전체에서 차감)

		// Apdex 계산
		double apdex = (satisfiedRequests + toleratingRequests * 0.5) / totalRequests;
		satisfactionScore.set(apdex);
	}

	/**
	 * Apdex 카운터 리셋 (테스트용 또는 주기적 리셋).
	 */
	public void resetApdexCounters() {
		totalRequests = 0;
		satisfiedRequests = 0;
		toleratingRequests = 0;
		satisfactionScore.set(1.0);
	}

	/**
	 * 현재 Apdex 점수 조회.
	 *
	 * @return Apdex 점수 (0.0 ~ 1.0)
	 */
	public double getApdexScore() {
		return satisfactionScore.get();
	}

	/**
	 * Apdex 등급 반환.
	 *
	 * @return Apdex 등급 ("Excellent", "Good", "Fair", "Poor", "Unacceptable")
	 */
	public String getApdexGrade() {
		double score = satisfactionScore.get();

		if (score >= 0.95) {
			return "Excellent";
		} else if (score >= 0.85) {
			return "Good";
		} else if (score >= 0.70) {
			return "Fair";
		} else if (score >= 0.50) {
			return "Poor";
		} else {
			return "Unacceptable";
		}
	}
}
