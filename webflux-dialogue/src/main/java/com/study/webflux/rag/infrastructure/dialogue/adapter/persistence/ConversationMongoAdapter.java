package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.entity.ConversationEntity;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
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
		ConversationEntity entity = new ConversationEntity(turn.id(), turn.query(), turn.response(),
			turn.createdAt());
		return mongoRepository.save(entity).map(saved -> ConversationTurn
			.withId(saved.id(), saved.query(), saved.response(), saved.createdAt()));
	}

	/** 최근 대화들을 생성 순서대로 조회합니다. */
	@Override
	public Flux<ConversationTurn> findRecent(int limit) {
		return mongoRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
			.map(entity -> ConversationTurn
				.withId(entity.id(), entity.query(), entity.response(), entity.createdAt()))
			.collectList().flatMapMany(list -> {
				java.util.Collections.reverse(list);
				return Flux.fromIterable(list);
			});
	}

	/** 전체 대화 기록을 스트리밍으로 조회합니다. */
	@Override
	public Flux<ConversationTurn> findAll() {
		return mongoRepository.findAll().map(entity -> ConversationTurn
			.withId(entity.id(), entity.query(), entity.response(), entity.createdAt()));
	}
}
