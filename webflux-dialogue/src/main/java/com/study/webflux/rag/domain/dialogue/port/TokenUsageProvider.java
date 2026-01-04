package com.study.webflux.rag.domain.dialogue.port;

import java.util.Optional;

import com.study.webflux.rag.domain.dialogue.model.TokenUsage;

public interface TokenUsageProvider {
	Optional<TokenUsage> getTokenUsage(String correlationId);
}
