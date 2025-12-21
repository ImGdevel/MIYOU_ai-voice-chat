package com.study.webflux.rag.domain.port.out;

import java.util.Optional;

import com.study.webflux.rag.domain.model.llm.TokenUsage;

public interface TokenUsageProvider {
	Optional<TokenUsage> getLastTokenUsage();
}
