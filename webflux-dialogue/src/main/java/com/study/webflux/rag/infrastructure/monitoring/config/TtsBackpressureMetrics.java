package com.study.webflux.rag.infrastructure.monitoring.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * TTS Backpressure 메트릭을 추적합니다.
 *
 * <p>
 * Phase 1A: Pipeline Bottleneck Analysis - TTS Endpoint Queue 크기 - Endpoint별 활성 요청 수
 */
@Component
public class TtsBackpressureMetrics {
	private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\[(\\d{3})]");
	private static final int NO_FAILURE_CODE = 0;
	private static final int UNKNOWN_FAILURE_CODE = -1;
	private static final int TIMEOUT_FAILURE_CODE = -408;
	private static final int NETWORK_FAILURE_CODE = -503;

	private final MeterRegistry meterRegistry;
	private final ConcurrentHashMap<String, TtsEndpoint> registeredEndpoints = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AtomicInteger> lastFailureCodes = new ConcurrentHashMap<>();

	public TtsBackpressureMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	/**
	 * TTS 엔드포인트를 등록하고 메트릭 Gauge를 생성합니다.
	 */
	public void registerEndpoints(List<TtsEndpoint> endpoints) {
		for (TtsEndpoint endpoint : endpoints) {
			if (!registeredEndpoints.containsKey(endpoint.getId())) {
				registeredEndpoints.put(endpoint.getId(), endpoint);
				AtomicInteger lastFailureCode = new AtomicInteger(NO_FAILURE_CODE);
				lastFailureCodes.put(endpoint.getId(), lastFailureCode);

				// Queue size (active requests) gauge
				Gauge.builder("tts.endpoint.queue.size", endpoint, TtsEndpoint::getActiveRequests)
					.tag("endpoint", endpoint.getId())
					.description("Number of active requests for TTS endpoint")
					.register(meterRegistry);

				// Health status gauge (1=HEALTHY, 0=TEMPORARY_FAILURE, -1=PERMANENT_FAILURE, -2=CLIENT_ERROR)
				Gauge.builder("tts.endpoint.health", endpoint, e -> {
					switch (e.getHealth()) {
						case HEALTHY :
							return 1;
						case TEMPORARY_FAILURE :
							return 0;
						case PERMANENT_FAILURE :
							return -1;
						case CLIENT_ERROR :
							return -2;
						default :
							return -1;
					}
				})
					.tag("endpoint", endpoint.getId())
					.description("TTS endpoint health status")
					.register(meterRegistry);

				// Last failure reason code (0=none, HTTP status code or negative internal code)
				Gauge.builder("tts.endpoint.last.failure.code", lastFailureCode, AtomicInteger::get)
					.tag("endpoint", endpoint.getId())
					.description(
						"TTS endpoint last failure code (0=none, HTTP status or negative internal code)")
					.register(meterRegistry);
			}
		}
	}

	/**
	 * 엔드포인트 목록을 업데이트합니다.
	 */
	public void updateEndpoints(List<TtsEndpoint> endpoints) {
		registerEndpoints(endpoints);
	}

	/**
	 * 엔드포인트 실패 정보를 카운트하고 마지막 실패 코드를 갱신합니다.
	 */
	public void recordEndpointFailure(String endpointId, String errorType, String errorMessage) {
		FailureReason reason = classifyReason(errorMessage);

		Counter.builder("tts.endpoint.failure.total")
			.tag("endpoint", endpointId)
			.tag("error_type", normalizeErrorType(errorType))
			.tag("reason_code", reason.reasonTag())
			.description("TTS endpoint failures by endpoint and reason")
			.register(meterRegistry)
			.increment();

		lastFailureCodes.computeIfAbsent(endpointId, ignored -> new AtomicInteger(NO_FAILURE_CODE))
			.set(reason.numericCode());
	}

	private String normalizeErrorType(String errorType) {
		if (errorType == null || errorType.isBlank()) {
			return "UNKNOWN";
		}
		return errorType;
	}

	private FailureReason classifyReason(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return new FailureReason("UNKNOWN", UNKNOWN_FAILURE_CODE);
		}

		Matcher matcher = HTTP_STATUS_PATTERN.matcher(errorMessage);
		if (matcher.find()) {
			int status = Integer.parseInt(matcher.group(1));
			return new FailureReason("HTTP_" + status, status);
		}

		String message = errorMessage.toLowerCase();
		if (message.contains("timeout")) {
			return new FailureReason("TIMEOUT", TIMEOUT_FAILURE_CODE);
		}
		if (message.contains("connection refused")
			|| message.contains("connection reset")
			|| message.contains("ioexception")) {
			return new FailureReason("NETWORK", NETWORK_FAILURE_CODE);
		}

		return new FailureReason("UNKNOWN", UNKNOWN_FAILURE_CODE);
	}

	private record FailureReason(
		String reasonTag,
		int numericCode) {
	}
}
