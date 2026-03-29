package com.miyou.app.infrastructure.dialogue.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationSessionDocument;
import reactor.core.publisher.Flux;

public interface ConversationSessionMongoRepository
	extends
		ReactiveMongoRepository<ConversationSessionDocument, String> {

	@Query("{'userId': ?0, 'deletedAt': null}")
	Flux<ConversationSessionDocument> findActiveByUserId(String userId, Sort sort);

	@Query("{'personaId': ?0, 'deletedAt': null}")
	Flux<ConversationSessionDocument> findActiveByPersonaId(String personaId, Sort sort);

	@Query("{'personaId': ?0, 'userId': ?1, 'deletedAt': null}")
	Flux<ConversationSessionDocument> findActiveByPersonaIdAndUserId(String personaId,
		String userId,
		Sort sort);
}
