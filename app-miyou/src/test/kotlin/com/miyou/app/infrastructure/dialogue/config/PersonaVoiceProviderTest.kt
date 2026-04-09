package com.miyou.app.infrastructure.dialogue.config

import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.model.VoiceSettings
import com.miyou.app.domain.voice.model.VoiceStyle
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties.PersonaVoiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PersonaVoiceProvider")
class PersonaVoiceProviderTest {
    private lateinit var voiceProvider: PersonaVoiceProvider
    private lateinit var defaultVoice: Voice

    @BeforeEach
    fun setUp() {
        defaultVoice =
            Voice
                .builder()
                .id("default-voice")
                .name("default")
                .provider("supertone")
                .settings(VoiceSettings(0, 1.0, 1.1))
                .language("ko")
                .style(VoiceStyle.NEUTRAL)
                .outputFormat(AudioFormat.MP3)
                .build()

        val properties = RagDialogueProperties()
        properties.supertone.outputFormat = "mp3"
        properties.personas =
            linkedMapOf(
                "maid" to
                    PersonaVoiceConfig().apply {
                        voiceId = "maid-voice"
                        language = "ko"
                        style = "neutral"
                        voiceSettings =
                            RagDialogueProperties.VoiceSettings().apply {
                                pitchShift = 0
                                pitchVariance = 1.0
                                speed = 1.1
                            }
                    },
                "interviewer" to
                    PersonaVoiceConfig().apply {
                        voiceId = "interviewer-voice"
                        language = "ko"
                        style = "neutral"
                        voiceSettings =
                            RagDialogueProperties.VoiceSettings().apply {
                                pitchShift = 0
                                pitchVariance = 1.0
                                speed = 1.0
                            }
                    },
            )

        voiceProvider = PersonaVoiceProvider(properties, defaultVoice)
    }

    @Test
    @DisplayName("기본 페르소나에는 기본 음성을 반환한다")
    fun getVoiceForPersona_returnsDefaultVoiceForDefaultPersona() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.ofNullable(null))

        assertThat(result).isEqualTo(defaultVoice)
    }

    @Test
    @DisplayName("설정된 페르소나에는 대응하는 음성을 반환한다")
    fun getVoiceForPersona_returnsConfiguredPersonaVoice() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("maid"))

        assertThat(result.id).isEqualTo("maid-voice")
        assertThat(result.name).isEqualTo("maid")
        assertThat(result.provider).isEqualTo("supertone")
        assertThat(result.language).isEqualTo("ko")
        assertThat(result.style).isEqualTo(VoiceStyle.NEUTRAL)
        assertThat(result.settings.speed).isEqualTo(1.1)
    }

    @Test
    @DisplayName("다른 페르소나에는 각자 설정된 음성을 반환한다")
    fun getVoiceForPersona_returnsDifferentPersonaVoice() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("interviewer"))

        assertThat(result.id).isEqualTo("interviewer-voice")
        assertThat(result.name).isEqualTo("interviewer")
        assertThat(result.settings.speed).isEqualTo(1.0)
    }

    @Test
    @DisplayName("알 수 없는 페르소나에는 기본 음성을 반환한다")
    fun getVoiceForPersona_returnsDefaultVoiceForUnknownPersona() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("unknown"))

        assertThat(result).isEqualTo(defaultVoice)
    }
}
