package com.study.webflux.rag.infrastructure.memory.config;

import java.time.Duration;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import reactor.core.publisher.Mono;

/**
 * Qdrant 컬렉션 존재 여부를 확인하고 필요 시 자동 생성합니다.
 */
@Slf4j
@Component
public class QdrantCollectionInitializer implements ApplicationRunner {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final RagDialogueProperties properties;
	private final WebClient webClient;

	public QdrantCollectionInitializer(RagDialogueProperties properties,
		WebClient.Builder webClientBuilder) {
		this.properties = properties;
		var qdrant = properties.getQdrant();
		WebClient.Builder builder = webClientBuilder.baseUrl(trimTrailingSlash(qdrant.getUrl()));
		if (StringUtils.hasText(qdrant.getApiKey())) {
			builder.defaultHeader("api-key", qdrant.getApiKey());
		}
		this.webClient = builder.build();
	}

	@Override
	public void run(ApplicationArguments args) {
		var qdrant = properties.getQdrant();
		if (!qdrant.isAutoCreateCollection()) {
			log.info("Qdrant 컬렉션 자동 생성이 비활성화되어 초기화를 건너뜁니다. collection={}",
				qdrant.getCollectionName());
			return;
		}

		boolean exists = collectionExists(qdrant.getCollectionName());
		if (exists) {
			log.info("Qdrant 컬렉션이 이미 존재합니다. collection={}", qdrant.getCollectionName());
			return;
		}

		createCollection(qdrant.getCollectionName(), qdrant.getVectorDimension());
	}

	private boolean collectionExists(String collectionName) {
		return webClient.get()
			.uri("/collections/{collectionName}", collectionName)
			.exchangeToMono(response -> {
				HttpStatusCode status = response.statusCode();
				if (status.is2xxSuccessful()) {
					return Mono.just(true);
				}
				if (status.value() == 404) {
					return Mono.just(false);
				}
				return response.bodyToMono(String.class)
					.defaultIfEmpty("")
					.flatMap(body -> Mono.error(new IllegalStateException(
						"Qdrant 컬렉션 조회 실패 status=" + status.value() + " body=" + body)));
			})
			.blockOptional(REQUEST_TIMEOUT)
			.orElse(false);
	}

	private void createCollection(String collectionName, int vectorDimension) {
		Map<String, Object> payload = Map.of("vectors",
			Map.of("size", vectorDimension, "distance", "Cosine"));

		webClient.put()
			.uri("/collections/{collectionName}", collectionName)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.exchangeToMono(response -> {
				HttpStatusCode status = response.statusCode();
				if (status.is2xxSuccessful() || status.value() == 409) {
					return Mono.empty();
				}
				return response.bodyToMono(String.class)
					.defaultIfEmpty("")
					.flatMap(body -> Mono.error(new IllegalStateException(
						"Qdrant 컬렉션 생성 실패 status=" + status.value() + " body=" + body)));
			})
			.block(REQUEST_TIMEOUT);

		log.info("Qdrant 컬렉션을 준비했습니다. collection={}, vectorDimension={}",
			collectionName,
			vectorDimension);
	}

	private String trimTrailingSlash(String url) {
		if (!StringUtils.hasText(url)) {
			return "";
		}
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}
}
