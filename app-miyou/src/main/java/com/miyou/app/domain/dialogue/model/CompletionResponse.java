package com.miyou.app.domain.dialogue.model;

public record CompletionResponse(
	String content,
	String model,
	int tokensUsed
) {
	public static CompletionResponse of(String content) {
		return new CompletionResponse(content, null, 0);
	}
}
