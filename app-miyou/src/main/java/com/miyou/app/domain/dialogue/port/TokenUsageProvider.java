package com.miyou.app.domain.dialogue.port;

import java.util.Optional;

import com.miyou.app.domain.dialogue.model.TokenUsage;

public interface TokenUsageProvider {
	Optional<TokenUsage> getTokenUsage(String correlationId);
}
