package com.miyou.app.domain.dialogue.service;

import java.util.List;

import reactor.core.publisher.Flux;

public class SentenceAssembler {

	public Flux<String> assemble(Flux<String> tokenStream) {
		return tokenStream.bufferUntil(this::isSentenceEnd).filter(list -> !list.isEmpty())
			.map(this::joinTokensToSentence);
	}

	private boolean isSentenceEnd(String token) {
		if (token == null || token.isEmpty()) {
			return false;
		}
		String trimmed = token.trim();
		return trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
			|| trimmed.endsWith("다.");
	}

	private String joinTokensToSentence(List<String> tokens) {
		return String.join("", tokens).trim();
	}
}
