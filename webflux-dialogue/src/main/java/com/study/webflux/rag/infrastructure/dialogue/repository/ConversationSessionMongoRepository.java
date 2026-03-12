package com.study.webflux.rag.infrastructure.dialogue.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.dialogue.adapter.persistence.document.ConversationSessionDocument;
import reactor.core.publisher.Flux;

public interface ConversationSessionMongoRepository
	extends
		ReactiveMongoRepository<ConversationSessionDocument, String> {

	Flux<ConversationSessionDocument> findByUserIdOrderByCreatedAtDesc(String userId);

	Flux<ConversationSessionDocument> findByPersonaIdOrderByCreatedAtDesc(String personaId);

	Flux<ConversationSessionDocument> findByPersonaIdAndUserIdOrderByCreatedAtDesc(String personaId,
		String userId);
}
