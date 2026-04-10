package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.model.VoiceSettings
import com.miyou.app.domain.voice.model.VoiceStyle
import com.miyou.app.domain.voice.port.VoiceSelectionPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties.PersonaVoiceConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PersonaVoiceProvider(
    private val properties: RagDialogueProperties,
    private val defaultVoice: Voice,
) : VoiceSelectionPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getVoiceForPersona(personaId: PersonaId): Voice {
        if (personaId == PersonaId.DEFAULT) {
            return defaultVoice
        }

        val personaKey = personaId.value
        val personas = properties.personas
        val config: PersonaVoiceConfig? = personas[personaKey]
        if (config != null) {
            return buildVoiceFromConfig(personaKey, config)
        }

        log.warn("페르소나 '{}'에 대한 설정을 찾지 못해 기본 음성 반환", personaKey)
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
}
