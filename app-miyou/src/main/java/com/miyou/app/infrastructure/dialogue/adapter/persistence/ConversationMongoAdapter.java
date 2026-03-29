package com.miyou.app.infrastructure.dialogue.adapter.persistence;

import org.springframework.data.domain.PageRequest;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.domain.dialogue.port.ConversationRepository;
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument;
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MongoDB 전용 ConversationRepository 구현체.
 * 캐싱이 필요한 경우 {@link ConversationCachingAdapter}를 사용한다.
 */
class ConversationMongoAdapter implements ConversationRepository {

	private final ConversationMongoRepository mongoRepository;

	ConversationMongoAdapter(ConversationMongoRepository mongoRepository) {
		this.mongoRepository = mongoRepository;
	}

	@Override
	public Mono<ConversationTurn> save(ConversationTurn turn) {
		ConversationDocument document = new ConversationDocument(
			turn.id(),
			turn.sessionId().value(),
			turn.query(),
			turn.response(),
			turn.createdAt());
		return mongoRepository.save(document).map(this::toConversationTurn);
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

	private ConversationTurn toConversationTurn(ConversationDocument document) {
		return ConversationTurn.withId(
			document.id(),
			ConversationSessionId.of(document.sessionId()),
			document.query(),
			document.response(),
			document.createdAt());
	}
}
