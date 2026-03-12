package com.study.webflux.rag.domain.memory.model;

import java.time.Instant;

/** 메모리 접근 지표 갱신 명령입니다. */
public record MemoryImportanceUpdateCommand(
	String memoryId,
	float newImportance,
	Instant lastAccessedAt,
	int accessCount
) {
	public MemoryImportanceUpdateCommand {
		if (memoryId == null || memoryId.isBlank()) {
			throw new IllegalArgumentException("memoryId must not be blank");
		}
		if (accessCount < 0) {
			throw new IllegalArgumentException("accessCount must not be negative");
		}
		memoryId = memoryId.trim();
	}
}
