package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;

public final class ConversationTurnFixture {

	public static final String DEFAULT_QUERY = "안녕하세요";
	public static final String DEFAULT_RESPONSE = "반갑습니다";

	private ConversationTurnFixture() {
	}

	public static ConversationTurn create() {
		return ConversationTurn.create(UserIdFixture.create(), DEFAULT_QUERY);
	}

	public static ConversationTurn create(UserId userId) {
		return ConversationTurn.create(userId, DEFAULT_QUERY);
	}

	public static ConversationTurn create(String query) {
		return ConversationTurn.create(UserIdFixture.create(), query);
	}

	public static ConversationTurn createWithResponse() {
		return ConversationTurn.create(UserIdFixture.create(), DEFAULT_QUERY)
			.withResponse(DEFAULT_RESPONSE);
	}

	public static ConversationTurn createWithResponse(UserId userId) {
		return ConversationTurn.create(userId, DEFAULT_QUERY)
			.withResponse(DEFAULT_RESPONSE);
	}

	public static ConversationTurn createWithId(String id) {
		return ConversationTurn
			.withId(id, UserIdFixture.create(), DEFAULT_QUERY, DEFAULT_RESPONSE, Instant.now());
	}
}
