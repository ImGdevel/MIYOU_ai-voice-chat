package com.study.webflux.rag.infrastructure.dialogue.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.domain.dialogue.entity.ConversationEntity;
import reactor.core.publisher.Flux;

public interface ConversationMongoRepository
	extends
		ReactiveMongoRepository<ConversationEntity, String> {
	Flux<ConversationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	Flux<ConversationEntity> findTop10ByOrderByCreatedAtDesc();
}
