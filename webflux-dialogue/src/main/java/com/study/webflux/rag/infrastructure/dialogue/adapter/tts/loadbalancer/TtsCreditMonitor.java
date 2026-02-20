package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer.circuit.CircuitBreakerState;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * TTS 엔드포인트 크레딧 모니터
 *
 * <p>
 * 백그라운드에서 주기적으로 각 엔드포인트의 남은 크레딧을 조회하고, 임계값 미만이면 알림 이벤트를 발행합니다.
 */
@Component
public class TtsCreditMonitor {
	private static final Logger log = LoggerFactory.getLogger(TtsCreditMonitor.class);

	private final TtsLoadBalancer loadBalancer;
	private final WebClient.Builder webClientBuilder;
	private final ApplicationEventPublisher eventPublisher;
	private final RagDialogueProperties.CreditMonitorConfig config;
	private final MeterRegistry meterRegistry;
	private final ConcurrentHashMap<String, Gauge> creditGauges = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Gauge> circuitStateGauges = new ConcurrentHashMap<>();

	public TtsCreditMonitor(
		TtsLoadBalancer loadBalancer,
		WebClient.Builder webClientBuilder,
		ApplicationEventPublisher eventPublisher,
		RagDialogueProperties properties,
		MeterRegistry meterRegistry) {
		this.loadBalancer = loadBalancer;
		this.webClientBuilder = webClientBuilder;
		this.eventPublisher = eventPublisher;
		this.config = properties.getSupertone().getCreditMonitor();
		this.meterRegistry = meterRegistry;

		// 메트릭 등록
		registerMetrics();
	}

	/**
	 * 엔드포인트별 메트릭 등록
	 */
	private void registerMetrics() {
		List<TtsEndpoint> endpoints = loadBalancer.getEndpoints();
		for (TtsEndpoint endpoint : endpoints) {
			// 크레딧 게이지
			creditGauges.put(endpoint.getId(),
				Gauge.builder("tts.endpoint.credits", endpoint, TtsEndpoint::getCredits)
					.tag("endpoint", endpoint.getId())
					.description("TTS 엔드포인트 남은 크레딧")
					.register(meterRegistry));

			// 서킷 브레이커 상태 게이지 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
			circuitStateGauges.put(endpoint.getId(),
				Gauge.builder("tts.endpoint.circuit_state",
					endpoint,
					e -> mapCircuitState(e.getCircuitBreaker().getState()))
					.tag("endpoint", endpoint.getId())
					.description("서킷 브레이커 상태 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
					.register(meterRegistry));
		}
	}

	/**
	 * 서킷 브레이커 상태를 숫자로 매핑
	 */
	private double mapCircuitState(CircuitBreakerState state) {
		return switch (state) {
			case CLOSED -> 0.0;
			case OPEN -> 1.0;
			case HALF_OPEN -> 2.0;
		};
	}

	/**
	 * 모든 엔드포인트의 크레딧을 주기적으로 폴링
	 *
	 * <p>
	 * 설정된 pollIntervalSeconds(기본 45초)마다 실행됩니다.
	 */
	@Scheduled(fixedDelayString = "#{${rag.dialogue.supertone.credit-monitor.poll-interval-seconds:45} * 1000}")
	public void pollAllEndpoints() {
		if (!config.isEnabled()) {
			return;
		}

		List<TtsEndpoint> endpoints = loadBalancer.getEndpoints();
		log.debug("크레딧 폴링 시작: {} 개 엔드포인트", endpoints.size());

		for (TtsEndpoint endpoint : endpoints) {
			pollEndpointCredits(endpoint)
				.doOnSuccess(credits -> handleCreditUpdate(endpoint, credits))
				.doOnError(error -> log.warn("엔드포인트 {} 크레딧 조회 실패: {}",
					endpoint.getId(),
					error.getMessage()))
				.subscribe();
		}
	}

	/**
	 * 단일 엔드포인트의 크레딧 조회
	 *
	 * @param endpoint
	 *            조회할 엔드포인트
	 * @return 남은 크레딧
	 */
	private Mono<Double> pollEndpointCredits(TtsEndpoint endpoint) {
		WebClient webClient = webClientBuilder
			.baseUrl(endpoint.getBaseUrl())
			.defaultHeader("Authorization", "Bearer " + endpoint.getApiKey())
			.build();

		return webClient.get()
			.uri("/v1/credits")
			.retrieve()
			.bodyToMono(CreditResponse.class)
			.map(CreditResponse::credits)
			.timeout(Duration.ofSeconds(5))
			.doOnError(error -> log.debug("엔드포인트 {} 크레딧 조회 에러: {}",
				endpoint.getId(),
				error.getMessage()));
	}

	/**
	 * 크레딧 업데이트 및 임계값 체크
	 *
	 * @param endpoint
	 *            엔드포인트
	 * @param credits
	 *            조회된 크레딧
	 */
	private void handleCreditUpdate(TtsEndpoint endpoint, Double credits) {
		if (credits == null) {
			return;
		}

		endpoint.updateCredits(credits);
		log.debug("엔드포인트 {} 크레딧 업데이트: {}", endpoint.getId(), credits);

		// 임계값 체크
		if (credits < config.getLowCreditThreshold()) {
			log.warn("엔드포인트 {} 크레딧 부족: {} < {}",
				endpoint.getId(),
				credits,
				config.getLowCreditThreshold());

			// PERMANENT_FAILURE로 설정하여 사용 중지
			endpoint.setHealth(TtsEndpoint.EndpointHealth.PERMANENT_FAILURE);

			// 알림 이벤트 발행
			TtsLowCreditEvent event = new TtsLowCreditEvent(
				endpoint.getId(),
				credits,
				config.getLowCreditThreshold());
			eventPublisher.publishEvent(event);
		}
	}
}
