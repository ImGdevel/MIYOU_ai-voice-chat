package com.miyou.app.application.dialogue.policy;

/** STT 처리 정책 — 파일 크기 제한 및 기본 언어를 보유합니다. */
public record SttPolicy(
	long maxFileSizeBytes,
	String defaultLanguage
) {
	public SttPolicy {
		if (maxFileSizeBytes <= 0) {
			throw new IllegalArgumentException("maxFileSizeBytes must be positive");
		}
		if (defaultLanguage == null || defaultLanguage.isBlank()) {
			throw new IllegalArgumentException("defaultLanguage must not be blank");
		}
		defaultLanguage = defaultLanguage.trim();
	}
}
