package com.study.webflux.rag.application.dialogue.policy;

public record PromptTemplatePolicy(
	String baseTemplate,
	String defaultPersonaTemplate,
	String commonTemplate,
	String configuredSystemPrompt
) {
}
