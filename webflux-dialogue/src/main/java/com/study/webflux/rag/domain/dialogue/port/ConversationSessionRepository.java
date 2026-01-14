package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationSessionRepository {

	Mono<ConversationSession> save(ConversationSession session);

	Mono<ConversationSession> findById(ConversationSessionId sessionId);

	Flux<ConversationSession> findByUserId(UserId userId);

	Flux<ConversationSession> findByPersonaId(PersonaId personaId);

	Flux<ConversationSession> findByPersonaIdAndUserId(PersonaId personaId, UserId userId);

	Mono<ConversationSession> softDelete(ConversationSessionId sessionId);
}
