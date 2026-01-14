package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;

public final class ConversationTurnFixture {

	public static final String DEFAULT_QUERY = "안녕하세요";
	public static final String DEFAULT_RESPONSE = "반갑습니다";

	private ConversationTurnFixture() {
	}

	public static ConversationTurn create() {
		return ConversationTurn.create(ConversationSessionFixture.createId(), DEFAULT_QUERY);
	}

	public static ConversationTurn create(ConversationSessionId sessionId) {
		return ConversationTurn.create(sessionId, DEFAULT_QUERY);
	}

	public static ConversationTurn create(String query) {
		return ConversationTurn.create(ConversationSessionFixture.createId(), query);
	}

	public static ConversationTurn createWithResponse() {
		return ConversationTurn.create(ConversationSessionFixture.createId(), DEFAULT_QUERY)
			.withResponse(DEFAULT_RESPONSE);
	}

	public static ConversationTurn createWithResponse(ConversationSessionId sessionId) {
		return ConversationTurn.create(sessionId, DEFAULT_QUERY).withResponse(DEFAULT_RESPONSE);
	}

	public static ConversationTurn createWithId(String id) {
		return ConversationTurn.withId(id,
			ConversationSessionFixture.createId(),
			DEFAULT_QUERY,
			DEFAULT_RESPONSE,
			Instant.now());
	}
}
