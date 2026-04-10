package com.miyou.app.infrastructure.dialogue.config.properties

import com.miyou.app.infrastructure.common.constants.DialogueConstants
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "rag.dialogue")
class RagDialogueProperties {
    var openai: OpenAi = OpenAi()
    var supertone: Supertone = Supertone()
    var stt: Stt = Stt()
    var qdrant: Qdrant = Qdrant()
    var memory: Memory = Memory()
    var cache: Cache = Cache()
    var systemPrompt: String = ""
    var systemPromptTemplate: String = "system/persona/maid"
    var commonSystemPromptTemplate: String = "system/common"
    var systemBasePromptTemplate: String = "system/base"
    var personas: MutableMap<String, PersonaVoiceConfig> = HashMap()

    class OpenAi {
        var apiKey: String? = null
        var baseUrl: String = "https://api.openai.com/v1"
        var model: String = "gpt-4.1-nano"
    }

    class Supertone {
        var endpoints: MutableList<TtsEndpointConfig> = ArrayList()
        var voiceId: String = DialogueConstants.Supertone.Voice.ADAM_ID
        var language: String = DialogueConstants.Supertone.Language.KOREAN
        var style: String = DialogueConstants.Supertone.Style.NEUTRAL
        var outputFormat: String = DialogueConstants.Supertone.OutputFormat.WAV
        var voiceSettings: VoiceSettings = VoiceSettings()
        var creditMonitor: CreditMonitorConfig = CreditMonitorConfig()
        var circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig()
    }

    class Stt {
        var model: String = "whisper-1"
        var language: String = "ko"
        var maxFileSizeBytes: Long = 25L * 1024L * 1024L
    }

    class Qdrant {
        var url: String = "http://localhost:6333"
        var apiKey: String? = null
        var vectorDimension: Int = 1536
        var collectionName: String = "user_memories"
        var autoCreateCollection: Boolean = true
    }

    class Memory {
        var embeddingModel: String = "text-embedding-3-small"
        var extractionModel: String = "gpt-4o-mini"
        var conversationThreshold: Int = 5
        var importanceBoost: Float = 0.05f
        var importanceThreshold: Float = 0.3f
    }

    class TtsEndpointConfig {
        var id: String? = null
        var apiKey: String? = null
        var baseUrl: String = DialogueConstants.Supertone.BASE_URL
        var maxConcurrentRequests: Int = 10
    }

    class PersonaVoiceConfig {
        @NotBlank
        var voiceId: String = ""
        var language: String = DialogueConstants.Supertone.Language.KOREAN
        var style: String = DialogueConstants.Supertone.Style.NEUTRAL
        var voiceSettings: VoiceSettings = VoiceSettings()
    }

    class CreditMonitorConfig {
        var enabled: Boolean = true
        var pollIntervalSeconds: Int = 45
        var lowCreditThreshold: Double = 10.0
    }

    class CircuitBreakerConfig {
        var baseBackoffSeconds: Int = 5
        var maxBackoffSeconds: Int = 300
    }

    class Cache {
        var maxHistorySize: Int = 20
        var ttlHours: Int = 24
    }

    class VoiceSettings {
        var pitchShift: Int = 0
        var pitchVariance: Double = 1.0
        var speed: Double = 1.1
    }
}
