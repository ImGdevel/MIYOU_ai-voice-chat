package com.study.webflux.rag.infrastructure.adapter.vectordb;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryType;
import com.study.webflux.rag.domain.port.out.VectorMemoryPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Primary
@Component
public class SpringAiVectorDbAdapter implements VectorMemoryPort {

	private final VectorStore vectorStore;

	public SpringAiVectorDbAdapter(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public Mono<Memory> upsert(Memory memory, List<Float> embedding) {
		return Mono.fromCallable(() -> {
			String id = memory.id() != null ? memory.id() : UUID.randomUUID().toString();

			Map<String, Object> metadata = new HashMap<>();
			metadata.put("type", memory.type().name());
			if (memory.importance() != null) {
				metadata.put("importance", memory.importance());
			}
			if (memory.createdAt() != null) {
				metadata.put("createdAt", memory.createdAt().getEpochSecond());
			}
			if (memory.lastAccessedAt() != null) {
				metadata.put("lastAccessedAt", memory.lastAccessedAt().getEpochSecond());
			}
			if (memory.accessCount() != null) {
				metadata.put("accessCount", memory.accessCount());
			}

			Document document = new Document(id, memory.content(), metadata);

			vectorStore.add(List.of(document));

			return memory.withId(id);
		}).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<Memory> search(List<Float> queryEmbedding, List<MemoryType> types, float importanceThreshold, int topK) {
		return Mono.fromCallable(() -> {
			String typeValues = types.stream()
				.map(t -> "'" + t.name() + "'")
				.reduce((a, b) -> a + ", " + b)
				.orElse("''");

			String filterExpression = String.format(
				"type in [%s] && importance >= %f",
				typeValues,
				importanceThreshold
			);

			SearchRequest request = SearchRequest.builder()
				.query("")
				.topK(topK)
				.similarityThreshold(0.0)
				.filterExpression(filterExpression)
				.build();

			return vectorStore.similaritySearch(request);
		})
		.subscribeOn(Schedulers.boundedElastic())
		.flatMapMany(Flux::fromIterable)
		.map(this::toMemory);
	}

	@Override
	public Mono<Void> updateImportance(String memoryId, float newImportance, Instant lastAccessedAt, int accessCount) {
		return Mono.fromRunnable(() -> {
			log.warn("메모리 ID={}의 중요도 업데이트는 현재 Spring AI에서 지원하지 않습니다", memoryId);
		})
		.subscribeOn(Schedulers.boundedElastic())
		.then();
	}

	private Memory toMemory(Document document) {
		Map<String, Object> metadata = document.getMetadata();

		String type = (String) metadata.get("type");
		if (type == null) {
			throw new IllegalStateException("Document " + document.getId() + " has no type metadata");
		}

		Float importance = null;
		Object importanceObj = metadata.get("importance");
		if (importanceObj instanceof Number) {
			importance = ((Number) importanceObj).floatValue();
		}

		Instant createdAt = null;
		Object createdAtObj = metadata.get("createdAt");
		if (createdAtObj instanceof Number) {
			createdAt = Instant.ofEpochSecond(((Number) createdAtObj).longValue());
		}

		Instant lastAccessedAt = null;
		Object lastAccessedAtObj = metadata.get("lastAccessedAt");
		if (lastAccessedAtObj instanceof Number) {
			lastAccessedAt = Instant.ofEpochSecond(((Number) lastAccessedAtObj).longValue());
		}

		Integer accessCount = null;
		Object accessCountObj = metadata.get("accessCount");
		if (accessCountObj instanceof Number) {
			accessCount = ((Number) accessCountObj).intValue();
		}

		MemoryType memoryType;
		try {
			memoryType = MemoryType.valueOf(type);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(
				"Document " + document.getId() + " has invalid type: " + type, e
			);
		}

		return new Memory(
			document.getId(),
			memoryType,
			document.getContent(),
			importance,
			createdAt,
			lastAccessedAt,
			accessCount
		);
	}
}
