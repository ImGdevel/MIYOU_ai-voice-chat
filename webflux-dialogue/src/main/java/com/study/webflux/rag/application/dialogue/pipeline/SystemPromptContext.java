package com.study.webflux.rag.application.dialogue.pipeline;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

/** 시스템 프롬프트 조립 입력 컨텍스트입니다. */
public record SystemPromptContext(
	PersonaId personaId,
	RetrievalContext retrievalContext,
	MemoryRetrievalResult memoryResult
) {
	public SystemPromptContext {
		personaId = personaId == null ? PersonaId.defaultPersona() : personaId;
	}
}
