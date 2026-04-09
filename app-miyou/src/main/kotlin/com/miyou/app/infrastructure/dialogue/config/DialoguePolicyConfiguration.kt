package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.application.dialogue.policy.DialogueExecutionPolicy
import com.miyou.app.application.dialogue.policy.PromptTemplatePolicy
import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.application.memory.policy.MemoryExtractionPolicy
import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.domain.dialogue.port.TemplateLoaderPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DialoguePolicyConfiguration {
    @Bean
    fun dialogueExecutionPolicy(properties: RagDialogueProperties): DialogueExecutionPolicy =
        DialogueExecutionPolicy(properties.openai.model)

    @Bean
    fun promptTemplatePolicy(
        properties: RagDialogueProperties,
        templateLoader: TemplateLoaderPort,
    ): PromptTemplatePolicy =
        PromptTemplatePolicy(
            resolveTemplate(templateLoader, properties.systemBasePromptTemplate),
            resolveTemplate(templateLoader, properties.systemPromptTemplate),
            resolveTemplate(templateLoader, properties.commonSystemPromptTemplate),
            properties.systemPrompt,
        )

    @Bean
    fun memoryRetrievalPolicy(properties: RagDialogueProperties): MemoryRetrievalPolicy {
        val memory = properties.memory
        return MemoryRetrievalPolicy(memory.importanceBoost, memory.importanceThreshold)
    }

    @Bean
    fun memoryExtractionPolicy(properties: RagDialogueProperties): MemoryExtractionPolicy =
        MemoryExtractionPolicy(properties.memory.conversationThreshold)

    @Bean
    fun sttPolicy(properties: RagDialogueProperties): SttPolicy {
        val stt = properties.stt
        return SttPolicy(stt.maxFileSizeBytes, stt.language)
    }

    private fun resolveTemplate(
        loader: TemplateLoaderPort,
        templateName: String?,
    ): String {
        if (templateName.isNullOrBlank()) {
            return ""
        }

        return try {
            loader.load(templateName).trim()
        } catch (exception: RuntimeException) {
            throw IllegalStateException("템플릿 파일을 로드할 수 없습니다. $templateName", exception)
        }
    }
}
