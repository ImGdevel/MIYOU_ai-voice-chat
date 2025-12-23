package com.study.webflux.rag.domain.dialogue.model;

public enum MessageRole {
	SYSTEM("system"),
	USER("user"),
	ASSISTANT("assistant");

	private final String value;

	MessageRole(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
