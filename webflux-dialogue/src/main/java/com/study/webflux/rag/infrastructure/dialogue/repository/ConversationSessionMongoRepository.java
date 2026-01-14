package com.study.webflux.rag.infrastructure.dialogue.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.domain.dialogue.entity.ConversationSessionEntity;
import reactor.core.publisher.Flux;

public interface ConversationSessionMongoRepository
	extends
		ReactiveMongoRepository<ConversationSessionEntity, String> {

	Flux<ConversationSessionEntity> findByUserIdOrderByCreatedAtDesc(String userId);

	Flux<ConversationSessionEntity> findByPersonaIdOrderByCreatedAtDesc(String personaId);

	Flux<ConversationSessionEntity> findByPersonaIdAndUserIdOrderByCreatedAtDesc(String personaId,
		String userId);
}
