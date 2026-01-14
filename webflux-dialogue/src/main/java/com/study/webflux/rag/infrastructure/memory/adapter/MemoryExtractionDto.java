package com.study.webflux.rag.infrastructure.memory.adapter;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.ExtractedMemory;
import com.study.webflux.rag.domain.memory.model.MemoryType;

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
