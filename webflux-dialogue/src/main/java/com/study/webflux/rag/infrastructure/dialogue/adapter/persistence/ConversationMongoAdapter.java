package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.entity.ConversationEntity;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.infrastructure.dialogue.repository.ConversationMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** MongoDB 기반 대화 저장소 어댑터입니다. */
@Component
public class ConversationMongoAdapter implements ConversationRepository {

	private final ConversationMongoRepository mongoRepository;

	public ConversationMongoAdapter(ConversationMongoRepository mongoRepository) {
		this.mongoRepository = mongoRepository;
	}

	/** 대화 기록을 MongoDB에 저장합니다. */
	@Override
	public Mono<ConversationTurn> save(ConversationTurn turn) {
		ConversationEntity entity = new ConversationEntity(
			turn.id(),
			turn.personaId().value(),
			turn.userId().value(),
			turn.query(),
			turn.response(),
			turn.createdAt());
		return mongoRepository.save(entity)
			.map(this::toConversationTurn);
	}

	/** 최근 대화들을 생성 순서대로 조회합니다. */
	@Override
	public Flux<ConversationTurn> findRecent(PersonaId personaId, UserId userId, int limit) {
		return mongoRepository
			.findByPersonaIdAndUserIdOrderByCreatedAtDesc(personaId.value(),
				userId.value(),
				PageRequest.of(0, limit))
			.map(this::toConversationTurn)
			.collectList()
			.flatMapMany(list -> {
				java.util.Collections.reverse(list);
				return Flux.fromIterable(list);
			});
	}

	/** 전체 대화 기록을 스트리밍으로 조회합니다. */
	@Override
	public Flux<ConversationTurn> findAll(PersonaId personaId, UserId userId) {
		return mongoRepository.findAllByPersonaIdAndUserId(personaId.value(), userId.value())
			.map(this::toConversationTurn);
	}

	private ConversationTurn toConversationTurn(ConversationEntity entity) {
		return ConversationTurn.withId(
			entity.id(),
			PersonaId.ofNullable(entity.personaId()),
			UserId.of(entity.userId()),
			entity.query(),
			entity.response(),
			entity.createdAt());
	}
}
