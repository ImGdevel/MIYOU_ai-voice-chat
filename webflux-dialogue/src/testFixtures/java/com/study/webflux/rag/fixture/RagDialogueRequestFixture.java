package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;

public final class RagDialogueRequestFixture {

	public static final String DEFAULT_TEXT = "테스트 질문입니다";

	private RagDialogueRequestFixture() {
	}

	public static RagDialogueRequest create() {
		return new RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, DEFAULT_TEXT,
			Instant.now());
	}

	public static RagDialogueRequest createWithText(String text) {
		return new RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, text,
			Instant.now());
	}

	public static RagDialogueRequest createWithSessionId(String sessionId) {
		return new RagDialogueRequest(sessionId, DEFAULT_TEXT, Instant.now());
	}

	public static RagDialogueRequest createWithBlankText() {
		return new RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, "",
			Instant.now());
	}

	public static RagDialogueRequest createWithNullTimestamp() {
		return new RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, DEFAULT_TEXT,
			null);
	}
}
