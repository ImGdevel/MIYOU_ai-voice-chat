package com.study.webflux.rag.domain.dialogue.model;

/** LLM 메시지에서 사용하는 참여자 역할을 정의합니다. */
public enum MessageRole {
	/** 모델 동작을 정의하는 시스템 프롬프트 역할입니다. */
	SYSTEM("system"),

	/** 사용자 발화를 나타내는 역할입니다. */
	USER("user"),

	/** 어시스턴트(LLM) 응답을 나타내는 역할입니다. */
	ASSISTANT("assistant");

	/** OpenAI API 직렬화를 위한 문자열 값입니다. */
	private final String value;

	/** 문자열 식별자로 역할을 초기화합니다. */
	MessageRole(String value) {
		this.value = value;
	}

	/** API 직렬화에 사용할 문자열을 반환합니다. */
	public String getValue() {
		return value;
	}
}
