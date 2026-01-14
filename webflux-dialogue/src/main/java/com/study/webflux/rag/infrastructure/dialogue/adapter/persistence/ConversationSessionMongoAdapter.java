package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.entity.ConversationSessionEntity;
import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationSessionRepository;
import com.study.webflux.rag.infrastructure.dialogue.repository.ConversationSessionMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ConversationSessionMongoAdapter implements ConversationSessionRepository {

	private final ConversationSessionMongoRepository mongoRepository;

	@Override
	public Mono<ConversationSession> save(ConversationSession session) {
		ConversationSessionEntity entity = toEntity(session);
		return mongoRepository.save(entity).map(this::toDomain);
	}

	@Override
	public Mono<ConversationSession> findById(ConversationSessionId sessionId) {
		return mongoRepository.findById(sessionId.value()).map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByUserId(UserId userId) {
		return mongoRepository.findByUserIdOrderByCreatedAtDesc(userId.value()).map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByPersonaId(PersonaId personaId) {
		return mongoRepository.findByPersonaIdOrderByCreatedAtDesc(personaId.value())
			.map(this::toDomain);
	}

	@Override
	public Flux<ConversationSession> findByPersonaIdAndUserId(PersonaId personaId, UserId userId) {
		return mongoRepository
			.findByPersonaIdAndUserIdOrderByCreatedAtDesc(personaId.value(), userId.value())
			.map(this::toDomain);
	}

	@Override
	public Mono<ConversationSession> softDelete(ConversationSessionId sessionId) {
		return mongoRepository.findById(sessionId.value())
			.flatMap(entity -> {
				ConversationSession session = toDomain(entity);
				return mongoRepository.save(toEntity(session.softDelete()));
			})
			.map(this::toDomain);
	}

	private ConversationSessionEntity toEntity(ConversationSession session) {
		return new ConversationSessionEntity(
			session.sessionId().value(),
			session.personaId().value(),
			session.userId().value(),
			session.createdAt(),
			session.deletedAt());
	}

	private ConversationSession toDomain(ConversationSessionEntity entity) {
		return new ConversationSession(
			ConversationSessionId.of(entity.sessionId()),
			PersonaId.of(entity.personaId()),
			UserId.of(entity.userId()),
			entity.createdAt(),
			entity.deletedAt());
	}
}
