package com.study.webflux.rag.fixture;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryType;

public final class MemoryFixture {

	public static final String DEFAULT_CONTENT = "사용자는 러닝을 좋아한다";
	public static final float DEFAULT_IMPORTANCE = 0.8f;

	private MemoryFixture() {
	}

	public static Memory createExperiential() {
		return Memory.create(UserIdFixture.create(),
			MemoryType.EXPERIENTIAL,
			DEFAULT_CONTENT,
			DEFAULT_IMPORTANCE);
	}

	public static Memory createExperiential(UserId userId) {
		return Memory.create(userId, MemoryType.EXPERIENTIAL, DEFAULT_CONTENT, DEFAULT_IMPORTANCE);
	}

	public static Memory createFactual() {
		return Memory.create(UserIdFixture.create(), MemoryType.FACTUAL, "사용자의 직업은 개발자다", 0.9f);
	}

	public static Memory createFactual(UserId userId) {
		return Memory.create(userId, MemoryType.FACTUAL, "사용자의 직업은 개발자다", 0.9f);
	}

	public static Memory createWithId(String id, MemoryType type) {
		return new Memory(id, UserIdFixture.create(), type, DEFAULT_CONTENT, DEFAULT_IMPORTANCE,
			Instant.now(), Instant.now(), 1);
	}

	public static Memory createWithId(String id, UserId userId, MemoryType type) {
		return new Memory(id, userId, type, DEFAULT_CONTENT, DEFAULT_IMPORTANCE,
			Instant.now(), Instant.now(), 1);
	}
}
