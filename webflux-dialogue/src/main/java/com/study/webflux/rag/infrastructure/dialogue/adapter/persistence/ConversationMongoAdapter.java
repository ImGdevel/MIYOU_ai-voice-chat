package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.entity.ConversationEntity;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.infrastructure.dialogue.repository.ConversationMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ConversationMongoAdapter implements ConversationRepository {

	private final ConversationMongoRepository mongoRepository;

	public ConversationMongoAdapter(ConversationMongoRepository mongoRepository) {
		this.mongoRepository = mongoRepository;
	}

	@Override
	public Mono<ConversationTurn> save(ConversationTurn turn) {
		ConversationEntity entity = new ConversationEntity(
			turn.id(),
			turn.sessionId().value(),
			turn.query(),
			turn.response(),
			turn.createdAt());
		return mongoRepository.save(entity).map(this::toConversationTurn);
	}

	@Override
	public Flux<ConversationTurn> findRecent(ConversationSessionId sessionId, int limit) {
		return mongoRepository
			.findBySessionIdOrderByCreatedAtDesc(sessionId.value(), PageRequest.of(0, limit))
			.map(this::toConversationTurn)
			.collectList()
			.flatMapMany(list -> {
				java.util.Collections.reverse(list);
				return Flux.fromIterable(list);
			});
	}

	@Override
	public Flux<ConversationTurn> findAll(ConversationSessionId sessionId) {
		return mongoRepository.findAllBySessionId(sessionId.value()).map(this::toConversationTurn);
	}

	private ConversationTurn toConversationTurn(ConversationEntity entity) {
		return ConversationTurn.withId(
			entity.id(),
			ConversationSessionId.of(entity.sessionId()),
			entity.query(),
			entity.response(),
			entity.createdAt());
	}
}
