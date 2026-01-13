package com.study.webflux.rag.infrastructure.memory.adapter;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.ExtractedMemory;
import com.study.webflux.rag.domain.memory.model.MemoryType;

record MemoryExtractionDto(
	String type,
	String content,
	float importance,
	String reasoning) {
	ExtractedMemory toExtractedMemory(PersonaId personaId, UserId userId) {
		return new ExtractedMemory(personaId, userId, MemoryType.valueOf(type.toUpperCase()),
			content,
			importance, reasoning);
	}
}
