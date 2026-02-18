package com.study.webflux.rag.fixture;

import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;

public final class ConversationSessionFixture {

	public static final String DEFAULT_SESSION_ID = "session-1";

	private ConversationSessionFixture() {
	}

	public static ConversationSession create() {
		return ConversationSession.create(PersonaId.defaultPersona(), UserIdFixture.create());
	}

	public static ConversationSession create(String sessionId) {
		return new ConversationSession(
			ConversationSessionId.of(sessionId),
			PersonaId.defaultPersona(),
			UserIdFixture.create(),
			java.time.Instant.now(),
			null);
	}

	public static ConversationSessionId createId() {
		return ConversationSessionId.of(DEFAULT_SESSION_ID);
	}

	public static ConversationSessionId createId(String sessionId) {
		return ConversationSessionId.of(sessionId);
	}
}
