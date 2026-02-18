package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryType;

public final class MemoryFixture {

	public static final String DEFAULT_CONTENT = "사용자는 러닝을 좋아한다";
	public static final float DEFAULT_IMPORTANCE = 0.8f;

	private MemoryFixture() {
	}

	public static Memory createExperiential() {
		return Memory.create(ConversationSessionFixture.createId(),
			MemoryType.EXPERIENTIAL,
			DEFAULT_CONTENT,
			DEFAULT_IMPORTANCE);
	}

	public static Memory createExperiential(ConversationSessionId sessionId) {
		return Memory.create(sessionId,
			MemoryType.EXPERIENTIAL,
			DEFAULT_CONTENT,
			DEFAULT_IMPORTANCE);
	}

	public static Memory createFactual() {
		return Memory.create(ConversationSessionFixture.createId(),
			MemoryType.FACTUAL,
			"사용자의 직업은 개발자다",
			0.9f);
	}

	public static Memory createFactual(ConversationSessionId sessionId) {
		return Memory.create(sessionId, MemoryType.FACTUAL, "사용자의 직업은 개발자다", 0.9f);
	}

	public static Memory createWithId(String id, MemoryType type) {
		return new Memory(id, ConversationSessionFixture.createId(), type, DEFAULT_CONTENT,
			DEFAULT_IMPORTANCE, Instant.now(), Instant.now(), 1);
	}

	public static Memory createWithId(String id, ConversationSessionId sessionId, MemoryType type) {
		return new Memory(id, sessionId, type, DEFAULT_CONTENT, DEFAULT_IMPORTANCE,
			Instant.now(), Instant.now(), 1);
	}
}
