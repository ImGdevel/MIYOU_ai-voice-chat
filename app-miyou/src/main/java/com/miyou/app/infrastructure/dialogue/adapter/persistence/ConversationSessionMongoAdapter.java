package com.miyou.app.infrastructure.dialogue.adapter.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.PersonaId;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository;
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationSessionDocument;
import com.miyou.app.infrastructure.dialogue.repository.ConversationSessionMongoRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ConversationSessionMongoAdapter implements ConversationSessionRepository {

	private static final Logger log = LoggerFactory.getLogger(ConversationSessionMongoAdapter.class);
	private static final String HISTORY_KEY_PREFIX = "dialogue:conversation:history:";
	private static final Sort CREATED_AT_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

	private final ConversationSessionMongoRepository mongoRepository;
	private final ReactiveRedisTemplate<String, String> redisTemplate;

	public ConversationSessionMongoAdapter(
		ConversationSessionMongoRepository mongoRepository,
		@Qualifier("reactiveRedisStringTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
		this.mongoRepository = mongoRepository;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Mono<ConversationSession> save(ConversationSession session) {
		ConversationSessionDocument document = toDocument(session);
		return mongoRepository.save(document).map(this::toDomain);
	}

	@Override
	public Mono<ConversationSession> findById(ConversationSessionId sessionId) {
		return mongoRepository.findById(sessionId.value()).map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByUserId(UserId userId) {
		return mongoRepository.findActiveByUserId(userId.value(), CREATED_AT_DESC).map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByPersonaId(PersonaId personaId) {
		return mongoRepository.findActiveByPersonaId(personaId.value(), CREATED_AT_DESC)
			.map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByPersonaIdAndUserId(PersonaId personaId, UserId userId) {
		return mongoRepository
			.findActiveByPersonaIdAndUserId(personaId.value(), userId.value(), CREATED_AT_DESC)
			.map(this::toDomain);
	}

	@Override
	public Mono<ConversationSession> softDelete(ConversationSessionId sessionId) {
		return mongoRepository.findById(sessionId.value())
			.flatMap(document -> {
				ConversationSession session = toDomain(document);
				return mongoRepository.save(toDocument(session.softDelete()));
			})
			.map(this::toDomain)
			.flatMap(deleted ->
				evictHistoryCache(sessionId)
					.onErrorResume(e -> {
						log.warn("Failed to evict history cache for session {} on soft-delete",
							sessionId.value(), e);
						return Mono.empty();
					})
					.thenReturn(deleted)
			);
	}

	private Mono<Void> evictHistoryCache(ConversationSessionId sessionId) {
		return redisTemplate.delete(HISTORY_KEY_PREFIX + sessionId.value()).then();
	}

	private ConversationSessionDocument toDocument(ConversationSession session) {
		return new ConversationSessionDocument(
			session.sessionId().value(),
			session.personaId().value(),
			session.userId().value(),
			session.createdAt(),
			session.deletedAt());
	}

	private ConversationSession toDomain(ConversationSessionDocument document) {
		return new ConversationSession(
			ConversationSessionId.of(document.sessionId()),
			PersonaId.of(document.personaId()),
			UserId.of(document.userId()),
			document.createdAt(),
			document.deletedAt());
	}
}
