package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.model.VoiceSettings
import com.miyou.app.domain.voice.model.VoiceStyle
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DialogueVoiceConfiguration {
    @Bean
    fun defaultVoice(properties: RagDialogueProperties): Voice {
        val supertone = properties.supertone
        val settings = supertone.voiceSettings
        return Voice
            .builder()
            .id(supertone.voiceId)
            .name("adam")
            .provider("supertone")
            .settings(
                VoiceSettings(settings.pitchShift, settings.pitchVariance, settings.speed),
            ).language(supertone.language)
            .style(VoiceStyle.fromString(supertone.style))
            .outputFormat(AudioFormat.fromString(supertone.outputFormat))
            .build()
    }
}
