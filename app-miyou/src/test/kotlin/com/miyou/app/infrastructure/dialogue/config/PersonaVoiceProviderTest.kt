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

@DisplayName("PersonaVoiceProvider 테스트")
class PersonaVoiceProviderTest {
    private lateinit var voiceProvider: PersonaVoiceProvider
    private lateinit var defaultVoice: Voice
    private lateinit var properties: RagDialogueProperties

    @BeforeEach
    fun setUp() {
        defaultVoice =
            Voice
                .builder()
                .id("2c5f135cb33f49a2c8882d")
                .name("default")
                .provider("supertone")
                .settings(VoiceSettings(0, 1.0, 1.1))
                .language("ko")
                .style(VoiceStyle.NEUTRAL)
                .outputFormat(AudioFormat.MP3)
                .build()

        properties = RagDialogueProperties()
        val supertone = RagDialogueProperties.Supertone()
        supertone.outputFormat = "mp3"
        properties.supertone = supertone

        val personas = linkedMapOf<String, PersonaVoiceConfig>()

        val maidConfig = PersonaVoiceConfig()
        maidConfig.voiceId = "2c5f135cb33f49a2c8882d"
        maidConfig.language = "ko"
        maidConfig.style = "neutral"
        val maidSettings = RagDialogueProperties.Supertone.VoiceSettings()
        maidSettings.pitchShift = 0
        maidSettings.pitchVariance = 1.0
        maidSettings.speed = 1.1
        maidConfig.voiceSettings = maidSettings
        personas["maid"] = maidConfig

        val interviewerConfig = PersonaVoiceConfig()
        interviewerConfig.voiceId = "4653d63d07d5340656b6bc"
        interviewerConfig.language = "ko"
        interviewerConfig.style = "neutral"
        val interviewerSettings = RagDialogueProperties.Supertone.VoiceSettings()
        interviewerSettings.pitchShift = 0
        interviewerSettings.pitchVariance = 1.0
        interviewerSettings.speed = 1.0
        interviewerConfig.voiceSettings = interviewerSettings
        personas["interviewer"] = interviewerConfig

        properties.personas = personas
        voiceProvider = PersonaVoiceProvider(properties, defaultVoice)
    }

    @Test
    @DisplayName("null PersonaId면 기본 Voice를 반환한다")
    fun testNullPersonaId() {
        val result = voiceProvider.getVoiceForPersona(null)
        assertThat(result).isEqualTo(defaultVoice)
    }

    @Test
    @DisplayName("DEFAULT PersonaId면 기본 Voice를 반환한다")
    fun testDefaultPersonaId() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.DEFAULT)
        assertThat(result).isEqualTo(defaultVoice)
    }

    @Test
    @DisplayName("maid 페르소나는 설정된 Voice를 반환한다")
    fun testMaidPersona() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("maid"))

        assertThat(result.id).isEqualTo("2c5f135cb33f49a2c8882d")
        assertThat(result.name).isEqualTo("maid")
        assertThat(result.provider).isEqualTo("supertone")
        assertThat(result.language).isEqualTo("ko")
        assertThat(result.style).isEqualTo(VoiceStyle.NEUTRAL)
        assertThat(result.settings.speed).isEqualTo(1.1)
    }

    @Test
    @DisplayName("interviewer 페르소나는 설정된 Voice를 반환한다")
    fun testInterviewerPersona() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("interviewer"))

        assertThat(result.id).isEqualTo("4653d63d07d5340656b6bc")
        assertThat(result.name).isEqualTo("interviewer")
        assertThat(result.provider).isEqualTo("supertone")
        assertThat(result.language).isEqualTo("ko")
        assertThat(result.style).isEqualTo(VoiceStyle.NEUTRAL)
        assertThat(result.settings.speed).isEqualTo(1.0)
    }

    @Test
    @DisplayName("설정되지 않은 페르소나는 기본 Voice를 반환한다")
    fun testUnknownPersona() {
        val result = voiceProvider.getVoiceForPersona(PersonaId.of("unknown"))
        assertThat(result).isEqualTo(defaultVoice)
    }

    @Test
    @DisplayName("각 페르소나는 서로 다른 Voice 객체를 반환한다")
    fun testDifferentVoicesForDifferentPersonas() {
        val maidVoice = voiceProvider.getVoiceForPersona(PersonaId.of("maid"))
        val interviewerVoice = voiceProvider.getVoiceForPersona(PersonaId.of("interviewer"))

        assertThat(maidVoice.id).isNotEqualTo(interviewerVoice.id)
        assertThat(maidVoice.settings.speed).isNotEqualTo(interviewerVoice.settings.speed)
    }
}
