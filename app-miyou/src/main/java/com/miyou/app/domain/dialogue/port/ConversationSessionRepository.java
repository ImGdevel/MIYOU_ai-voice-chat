package com.miyou.app.domain.dialogue.port;

import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.PersonaId;
import com.miyou.app.domain.dialogue.model.UserId;
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
