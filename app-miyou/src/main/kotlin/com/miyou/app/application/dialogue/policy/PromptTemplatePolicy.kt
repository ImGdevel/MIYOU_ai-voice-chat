package com.miyou.app.application.dialogue.policy

class PromptTemplatePolicy(
    baseTemplate: String,
    defaultPersonaTemplate: String,
    commonTemplate: String,
    configuredSystemPrompt: String,
) {
    val baseTemplate: String = baseTemplate.normalize()
    val defaultPersonaTemplate: String = defaultPersonaTemplate.normalize()
    val commonTemplate: String = commonTemplate.normalize()
    val configuredSystemPrompt: String = configuredSystemPrompt.normalize()

    private fun String?.normalize(): String = this ?: ""
}
