package com.study.webflux.rag.infrastructure.adapter.vectordb;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryType;
import com.study.webflux.rag.domain.port.out.VectorMemoryPort;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.Range;
import io.qdrant.client.grpc.Points.Match;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Primary
@Component
public class SpringAiVectorDbAdapter implements VectorMemoryPort {

	private final VectorStore vectorStore;
	private final QdrantClient qdrantClient;
	private final String collectionName;

	public SpringAiVectorDbAdapter(VectorStore vectorStore, QdrantClient qdrantClient) {
		this.vectorStore = vectorStore;
		this.qdrantClient = qdrantClient;

		if (vectorStore instanceof QdrantVectorStore qdrantVectorStore) {
			this.collectionName = "vector_store";
		} else {
			this.collectionName = "vector_store";
			log.warn("VectorStore is not QdrantVectorStore, using default collection name");
		}
	}

	private List<Float> toFloatList(double[] doubleArray) {
		List<Float> result = new java.util.ArrayList<>(doubleArray.length);
		for (double v : doubleArray) {
			result.add((float) v);
		}
		return result;
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
				metadata.put("createdAt", memory.createdAt().toEpochMilli());
			}
			if (memory.lastAccessedAt() != null) {
				metadata.put("lastAccessedAt", memory.lastAccessedAt().toEpochMilli());
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
    public Flux<Memory> search(
            List<Float> queryEmbedding,
            List<MemoryType> types,
            float importanceThreshold,
            int topK
    ) {
        return Mono.fromCallable(() -> {
                    Filter.Builder filterBuilder = Filter.newBuilder();

                    if (importanceThreshold > 0) {
                        filterBuilder.addMust(
                            Condition.newBuilder()
                                .setField(
                                    FieldCondition.newBuilder()
                                        .setKey("importance")
                                        .setRange(
                                            Range.newBuilder()
                                                .setGte(importanceThreshold)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        );
                    }

                    if (types != null && !types.isEmpty()) {
                        Filter.Builder typeFilterBuilder = Filter.newBuilder();
                        for (MemoryType type : types) {
                            typeFilterBuilder.addShould(
                                Condition.newBuilder()
                                    .setField(
                                        FieldCondition.newBuilder()
                                            .setKey("type")
                                            .setMatch(
                                                Match.newBuilder()
                                                    .setKeyword(type.name())
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            );
                        }
                        filterBuilder.addMust(
                            Condition.newBuilder()
                                .setFilter(typeFilterBuilder.build())
                                .build()
                        );
                    }

                    SearchPoints searchPoints = SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllVector(queryEmbedding)
                            .setLimit(topK)
                            .setWithPayload(io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                            .setFilter(filterBuilder.build())
                            .build();

                    List<ScoredPoint> results = qdrantClient.searchAsync(searchPoints).get();

                    return results.stream()
                            .map(this::toMemoryFromScoredPoint)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
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
            createdAt = Instant.ofEpochMilli(((Number) createdAtObj).longValue());
		}

		Instant lastAccessedAt = null;
		Object lastAccessedAtObj = metadata.get("lastAccessedAt");
		if (lastAccessedAtObj instanceof Number) {
            lastAccessedAt = Instant.ofEpochMilli(((Number) lastAccessedAtObj).longValue());
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

	private Memory toMemoryFromScoredPoint(ScoredPoint point) {
		Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = point.getPayloadMap();

		String id = point.getId().hasNum()
			? String.valueOf(point.getId().getNum())
			: point.getId().getUuid();

		String content = payload.containsKey("content")
			? payload.get("content").getStringValue()
			: "";

		String typeStr = payload.containsKey("type")
			? payload.get("type").getStringValue()
			: null;

		if (typeStr == null) {
			throw new IllegalStateException("Point " + id + " has no type");
		}

		MemoryType type = MemoryType.valueOf(typeStr);

		Float importance = payload.containsKey("importance")
			? (float) payload.get("importance").getDoubleValue()
			: null;

		Instant createdAt = payload.containsKey("createdAt")
			? Instant.ofEpochMilli((long) payload.get("createdAt").getDoubleValue())
			: null;

		Instant lastAccessedAt = payload.containsKey("lastAccessedAt")
			? Instant.ofEpochMilli((long) payload.get("lastAccessedAt").getDoubleValue())
			: null;

		Integer accessCount = payload.containsKey("accessCount")
			? (int) payload.get("accessCount").getDoubleValue()
			: null;

		return new Memory(id, type, content, importance, createdAt, lastAccessedAt, accessCount);
	}
}
