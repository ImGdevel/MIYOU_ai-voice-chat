package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.application.dialogue.dto.RagDialogueRequest;

public final class RagDialogueRequestFixture {

	public static final String DEFAULT_TEXT = "테스트 질문입니다";

	private RagDialogueRequestFixture() {
	}

	public static RagDialogueRequest create() {
		return new RagDialogueRequest(UserIdFixture.DEFAULT_USER_ID, DEFAULT_TEXT, Instant.now());
	}

	public static RagDialogueRequest createWithText(String text) {
		return new RagDialogueRequest(UserIdFixture.DEFAULT_USER_ID, text, Instant.now());
	}

	public static RagDialogueRequest createWithUserId(String userId) {
		return new RagDialogueRequest(userId, DEFAULT_TEXT, Instant.now());
	}

	public static RagDialogueRequest createWithBlankText() {
		return new RagDialogueRequest(UserIdFixture.DEFAULT_USER_ID, "", Instant.now());
	}

	public static RagDialogueRequest createWithNullTimestamp() {
		return new RagDialogueRequest(UserIdFixture.DEFAULT_USER_ID, DEFAULT_TEXT, null);
	}
}
