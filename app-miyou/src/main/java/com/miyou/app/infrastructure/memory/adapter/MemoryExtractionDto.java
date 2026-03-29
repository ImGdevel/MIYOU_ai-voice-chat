package com.miyou.app.infrastructure.memory.adapter;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.memory.model.ExtractedMemory;
import com.miyou.app.domain.memory.model.MemoryType;

record MemoryExtractionDto(
	String type,
	String content,
	float importance,
	String reasoning) {
	ExtractedMemory toExtractedMemory(ConversationSessionId sessionId) {
		return new ExtractedMemory(sessionId, MemoryType.valueOf(type.toUpperCase()),
			content,
			importance, reasoning);
	}
}
