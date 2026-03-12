package com.study.webflux.rag.application.dialogue.policy;

/** STT 처리 정책 — 파일 크기 제한 및 기본 언어를 보유합니다. */
public record SttPolicy(
	long maxFileSizeBytes,
	String defaultLanguage
) {
}
