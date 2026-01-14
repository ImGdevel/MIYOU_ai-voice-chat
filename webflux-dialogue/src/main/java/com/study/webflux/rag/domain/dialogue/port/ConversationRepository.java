package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationRepository {

	Mono<ConversationTurn> save(ConversationTurn turn);

	Flux<ConversationTurn> findRecent(ConversationSessionId sessionId, int limit);

	Flux<ConversationTurn> findAll(ConversationSessionId sessionId);
}
