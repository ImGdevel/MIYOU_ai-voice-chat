package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.model.VoiceSettings
import com.miyou.app.domain.voice.model.VoiceStyle
import com.miyou.app.domain.voice.port.VoiceSelectionPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties.PersonaVoiceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PersonaVoiceProvider(
    private val properties: RagDialogueProperties,
    private val defaultVoice: Voice,
) : VoiceSelectionPort {
    private val log = KotlinLogging.logger {}

    override fun getVoiceForPersona(personaId: String): Voice {
        if (personaId.isBlank() || personaId == DEFAULT_PERSONA_KEY) {
            return defaultVoice
        }

        val personaKey = personaId
        val personas = properties.personas
        val config: PersonaVoiceConfig? = personas[personaKey]
        if (config != null) {
            return buildVoiceFromConfig(personaKey, config)
        }

        log.warn { "페르소나 '$personaKey'에 대한 설정을 찾지 못해 기본 음성 반환" }
        return defaultVoice
    }

    private fun buildVoiceFromConfig(
        personaKey: String,
        config: PersonaVoiceConfig,
    ): Voice {
        val voiceSettingsConfig = config.voiceSettings
        val settings =
            VoiceSettings(
                voiceSettingsConfig.pitchShift,
                voiceSettingsConfig.pitchVariance,
                voiceSettingsConfig.speed,
            )

        return Voice
            .builder()
            .id(config.voiceId)
            .name(personaKey)
            .provider("supertone")
            .settings(settings)
            .language(config.language)
            .style(VoiceStyle.fromString(config.style))
            .outputFormat(AudioFormat.fromString(properties.supertone.outputFormat))
            .build()
    }

    companion object {
        private const val DEFAULT_PERSONA_KEY = "default"
    }
}
