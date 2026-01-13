package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface ConversationCounterPort {
	Mono<Long> increment(PersonaId personaId, UserId userId);

	Mono<Long> get(PersonaId personaId, UserId userId);

	Mono<Void> reset(PersonaId personaId, UserId userId);

	default Mono<Long> increment(UserId userId) {
		return increment(PersonaId.defaultPersona(), userId);
	}

	default Mono<Long> get(UserId userId) {
		return get(PersonaId.defaultPersona(), userId);
	}

	default Mono<Void> reset(UserId userId) {
		return reset(PersonaId.defaultPersona(), userId);
	}
}
