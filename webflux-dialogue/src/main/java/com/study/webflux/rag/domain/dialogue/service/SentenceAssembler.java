package com.study.webflux.rag.domain.dialogue.service;

import reactor.core.publisher.Flux;

public class SentenceAssembler {

	private final int maxChars;

	public SentenceAssembler() {
		this(250);
	}

	public SentenceAssembler(int maxChars) {
		this.maxChars = maxChars;
	}

	public Flux<String> assemble(Flux<String> tokenStream) {
		return tokenStream
			.scan(State.empty(), (state, token) -> state.accumulate(token, maxChars))
			.filter(State::shouldFlush)
			.map(State::text);
	}

	private static boolean isSentenceEnd(String token) {
		if (token == null || token.isEmpty()) {
			return false;
		}
		String trimmed = token.trim();
		return trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
			|| trimmed.endsWith("다.");
	}

	private record State(
		StringBuilder buffer,
		String flushedText,
		boolean flush) {

		static State empty() {
			return new State(new StringBuilder(), "", false);
		}

		State accumulate(String token, int maxChars) {
			StringBuilder next = new StringBuilder(buffer).append(token);
			boolean shouldFlush = isSentenceEnd(token) || next.length() >= maxChars;
			String text = shouldFlush ? next.toString().trim() : "";
			return new State(shouldFlush ? new StringBuilder() : next, text, shouldFlush);
		}

		boolean shouldFlush() {
			return flush && !flushedText.isEmpty();
		}

		String text() {
			return flushedText;
		}
	}
}
