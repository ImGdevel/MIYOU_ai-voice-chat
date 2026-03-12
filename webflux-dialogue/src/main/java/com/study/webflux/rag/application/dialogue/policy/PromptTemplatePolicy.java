package com.study.webflux.rag.application.dialogue.policy;

public record PromptTemplatePolicy(
	String baseTemplate,
	String defaultPersonaTemplate,
	String commonTemplate,
	String configuredSystemPrompt
) {
	public PromptTemplatePolicy {
		baseTemplate = normalize(baseTemplate);
		defaultPersonaTemplate = normalize(defaultPersonaTemplate);
		commonTemplate = normalize(commonTemplate);
		configuredSystemPrompt = normalize(configuredSystemPrompt);
	}

	private static String normalize(String template) {
		return template == null ? "" : template;
	}
}
