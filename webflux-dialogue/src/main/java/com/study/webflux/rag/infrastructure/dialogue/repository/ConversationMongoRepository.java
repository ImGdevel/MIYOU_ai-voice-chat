package com.study.webflux.rag.infrastructure.dialogue.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.dialogue.adapter.persistence.document.ConversationDocument;
import reactor.core.publisher.Flux;

public interface ConversationMongoRepository
	extends
		ReactiveMongoRepository<ConversationDocument, String> {

	Flux<ConversationDocument> findBySessionIdOrderByCreatedAtDesc(String sessionId,
		Pageable pageable);

	Flux<ConversationDocument> findBySessionIdOrderByCreatedAtAsc(String sessionId,
		Pageable pageable);
}
