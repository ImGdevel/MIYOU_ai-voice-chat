package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.infrastructure.dialogue.adapter.persistence.document.ConversationDocument;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.dialogue.repository.ConversationMongoRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ConversationCachingAdapter implements ConversationRepository {

	private static final Logger log = LoggerFactory.getLogger(ConversationCachingAdapter.class);

	private static final String KEY_PREFIX = "dialogue:conversation:history:";

	private final ConversationMongoRepository mongoRepository;
	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final int maxCacheSize;
	private final Duration cacheTtl;
	private final ObjectMapper objectMapper;

	public ConversationCachingAdapter(
		ConversationMongoRepository mongoRepository,
		@Qualifier("reactiveRedisStringTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
		RagDialogueProperties properties) {
		this.mongoRepository = mongoRepository;
		this.redisTemplate = redisTemplate;
		this.maxCacheSize = properties.getCache().getMaxHistorySize();
		this.cacheTtl = Duration.ofHours(properties.getCache().getTtlHours());
		this.objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Override
	public Mono<ConversationTurn> save(ConversationTurn turn) {
		ConversationDocument doc = toDocument(turn);
		return mongoRepository.save(doc)
			.map(this::toConversationTurn)
			.flatMap(saved ->
				appendToCache(saved)
					.onErrorResume(e -> {
						log.warn("Redis cache write failed for session {}, continuing without cache",
							saved.sessionId().value(), e);
						return Mono.empty();
					})
					.thenReturn(saved)
			);
	}

	@Override
	public Flux<ConversationTurn> findRecent(ConversationSessionId sessionId, int limit) {
		String key = historyKey(sessionId);
		return redisTemplate.opsForList()
			.range(key, -limit, -1)
			.map(this::deserialize)
			.onErrorResume(e -> {
				log.warn("Redis cache read failed for session {}, falling back to MongoDB",
					sessionId.value(), e);
				return Flux.empty();
			})
			.switchIfEmpty(
				loadFromMongoAndWarmup(sessionId, key, limit)
			);
	}

	private Flux<ConversationTurn> loadFromMongoAndWarmup(ConversationSessionId sessionId,
		String key, int limit) {
		return mongoRepository
			.findBySessionIdOrderByCreatedAtAsc(sessionId.value(), PageRequest.of(0, limit))
			.map(this::toConversationTurn)
			.collectList()
			.flatMapMany(list ->
				warmupCache(key, list)
					.onErrorResume(e -> {
						log.warn("Redis cache warmup failed for session {}", sessionId.value(), e);
						return Mono.empty();
					})
					.thenMany(Flux.fromIterable(list))
			);
	}

	private Mono<Void> appendToCache(ConversationTurn turn) {
		String key = historyKey(turn.sessionId());
		String json = serialize(turn);
		return redisTemplate.opsForList().rightPush(key, json)
			.then(redisTemplate.opsForList().trim(key, -maxCacheSize, -1))
			.then(redisTemplate.expire(key, cacheTtl))
			.then();
	}

	private Mono<Void> warmupCache(String key, List<ConversationTurn> turns) {
		if (turns.isEmpty()) {
			return Mono.empty();
		}
		String[] values = turns.stream().map(this::serialize).toArray(String[]::new);
		return redisTemplate.opsForList().rightPushAll(key, values)
			.then(redisTemplate.opsForList().trim(key, -maxCacheSize, -1))
			.then(redisTemplate.expire(key, cacheTtl))
			.then();
	}

	public Mono<Void> evict(ConversationSessionId sessionId) {
		return redisTemplate.delete(historyKey(sessionId)).then();
	}

	private String historyKey(ConversationSessionId sessionId) {
		return KEY_PREFIX + sessionId.value();
	}

	private String serialize(ConversationTurn turn) {
		try {
			return objectMapper.writeValueAsString(new ConversationTurnDto(
				turn.id(),
				turn.sessionId().value(),
				turn.query(),
				turn.response(),
				turn.createdAt()));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize ConversationTurn", e);
		}
	}

	private ConversationTurn deserialize(String json) {
		try {
			ConversationTurnDto dto = objectMapper.readValue(json, ConversationTurnDto.class);
			return ConversationTurn.withId(
				dto.id(),
				ConversationSessionId.of(dto.sessionId()),
				dto.query(),
				dto.response(),
				dto.createdAt());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize ConversationTurn", e);
		}
	}

	private ConversationDocument toDocument(ConversationTurn turn) {
		return new ConversationDocument(
			turn.id(),
			turn.sessionId().value(),
			turn.query(),
			turn.response(),
			turn.createdAt());
	}

	private ConversationTurn toConversationTurn(ConversationDocument doc) {
		return ConversationTurn.withId(
			doc.id(),
			ConversationSessionId.of(doc.sessionId()),
			doc.query(),
			doc.response(),
			doc.createdAt());
	}

	private record ConversationTurnDto(
		String id,
		String sessionId,
		String query,
		String response,
		Instant createdAt) {
	}
}
