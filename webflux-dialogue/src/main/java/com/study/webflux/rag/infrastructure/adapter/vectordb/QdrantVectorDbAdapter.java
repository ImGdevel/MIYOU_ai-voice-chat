package com.study.webflux.rag.infrastructure.adapter.vectordb;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryType;
import com.study.webflux.rag.domain.port.out.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantFilter;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantPoint;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantScoredPoint;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantSearchRequest;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantSearchResponse;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantUpdatePayloadRequest;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantUpsertRequest;
import com.study.webflux.rag.infrastructure.adapter.vectordb.dto.QdrantUpsertResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class QdrantVectorDbAdapter implements VectorMemoryPort {

	private final WebClient webClient;
	private final String collectionName;

	public QdrantVectorDbAdapter(WebClient.Builder webClientBuilder, QdrantConfig config) {
		WebClient.Builder builder = webClientBuilder
			.baseUrl(config.url())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

		if (config.apiKey() != null && !config.apiKey().isBlank()) {
			builder.defaultHeader("api-key", config.apiKey());
		}

		this.webClient = builder.build();
		this.collectionName = config.collectionName();
	}

	@Override
	public Mono<Memory> upsert(Memory memory, List<Float> embedding) {
		String id = memory.id() != null ? memory.id() : UUID.randomUUID().toString();

		Map<String, Object> payload = new HashMap<>();
		payload.put("content", memory.content());
		payload.put("type", memory.type().name());

		if (memory.importance() != null) {
			payload.put("importance", memory.importance());
		}
		if (memory.createdAt() != null) {
			payload.put("createdAt", memory.createdAt().getEpochSecond());
		}
		if (memory.lastAccessedAt() != null) {
			payload.put("lastAccessedAt", memory.lastAccessedAt().getEpochSecond());
		}
		if (memory.accessCount() != null) {
			payload.put("accessCount", memory.accessCount());
		}

		QdrantPoint point = new QdrantPoint(id, embedding, payload);
		QdrantUpsertRequest request = new QdrantUpsertRequest(List.of(point));

		return webClient.put()
			.uri("/collections/{collection}/points", collectionName)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(QdrantUpsertResponse.class)
			.map(response -> memory.withId(id));
	}

	@Override
	public Flux<Memory> search(List<Float> queryEmbedding, List<MemoryType> types, float importanceThreshold,
		int topK) {
		List<String> typeStrings = types.stream()
			.map(MemoryType::name)
			.collect(Collectors.toList());

		QdrantFilter filter = new QdrantFilter(List.of(
			QdrantFilter.FilterCondition.matchAny("type", typeStrings),
			QdrantFilter.FilterCondition.rangeGte("importance", importanceThreshold)
		));

		QdrantSearchRequest request = new QdrantSearchRequest(
			queryEmbedding,
			topK,
			true,
			filter
		);

		return webClient.post()
			.uri("/collections/{collection}/points/search", collectionName)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(QdrantSearchResponse.class)
			.flatMapMany(response -> Flux.fromIterable(response.result()))
			.map(this::toMemory);
	}

	@Override
	public Mono<Void> updateImportance(String memoryId, float newImportance, Instant lastAccessedAt, int accessCount) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("importance", newImportance);
		payload.put("lastAccessedAt", lastAccessedAt.getEpochSecond());
		payload.put("accessCount", accessCount);

		QdrantUpdatePayloadRequest request = new QdrantUpdatePayloadRequest(
			List.of(memoryId),
			payload
		);

		return webClient.post()
			.uri("/collections/{collection}/points/payload", collectionName)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(Void.class)
            .doOnError(e -> log.warn("메모리 ID={}의 중요도 업데이트 실패: {}", memoryId, e.getMessage()))
			.onErrorResume(e -> Mono.empty());
	}

	private Memory toMemory(QdrantScoredPoint point) {
		Map<String, Object> payload = point.payload();

		Object typeObj = payload.get("type");
		Object contentObj = payload.get("content");

		if (!(typeObj instanceof String) || !(contentObj instanceof String)) {
			throw new IllegalStateException(
				"잘못된 페이로드: 포인트 " + point.id() + "의 type/content 누락 또는 잘못됨"
			);
		}

		String type = (String)typeObj;
		String content = (String)contentObj;

		Float importance = null;
		if (payload.containsKey("importance")) {
			Object importanceObj = payload.get("importance");
			if (importanceObj instanceof Number) {
				importance = ((Number)importanceObj).floatValue();
			}
		}

		Instant createdAt = null;
		if (payload.containsKey("createdAt")) {
			Object createdAtObj = payload.get("createdAt");
			if (createdAtObj instanceof Number) {
				createdAt = Instant.ofEpochSecond(((Number)createdAtObj).longValue());
			}
		}

		Instant lastAccessedAt = null;
		if (payload.containsKey("lastAccessedAt")) {
			Object lastAccessedAtObj = payload.get("lastAccessedAt");
			if (lastAccessedAtObj instanceof Number) {
				lastAccessedAt = Instant.ofEpochSecond(((Number)lastAccessedAtObj).longValue());
			}
		}

		Integer accessCount = null;
		if (payload.containsKey("accessCount")) {
			Object accessCountObj = payload.get("accessCount");
			if (accessCountObj instanceof Number) {
				accessCount = ((Number)accessCountObj).intValue();
			}
		}

		return new Memory(
			point.id(),
			MemoryType.valueOf(type),
			content,
			importance,
			createdAt,
			lastAccessedAt,
			accessCount
		);
	}
}
