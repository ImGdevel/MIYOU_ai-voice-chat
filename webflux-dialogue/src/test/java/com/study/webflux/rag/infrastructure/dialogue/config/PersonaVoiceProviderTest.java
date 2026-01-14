package com.study.webflux.rag.infrastructure.dialogue.config;

import java.util.HashMap;
import java.util.Map;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.domain.voice.model.VoiceSettings;
import com.study.webflux.rag.domain.voice.model.VoiceStyle;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties.PersonaVoiceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonaVoiceProvider 테스트")
class PersonaVoiceProviderTest {

	private PersonaVoiceProvider voiceProvider;
	private Voice defaultVoice;
	private RagDialogueProperties properties;

	@BeforeEach
	void setUp() {
		// Default Voice 설정
		defaultVoice = Voice.builder()
			.id("2c5f135cb33f49a2c8882d")
			.name("default")
			.provider("supertone")
			.settings(new VoiceSettings(0, 1.0, 1.1))
			.language("ko")
			.style(VoiceStyle.NEUTRAL)
			.outputFormat(AudioFormat.MP3)
			.build();

		// Properties 설정
		properties = new RagDialogueProperties();
		var supertone = new RagDialogueProperties.Supertone();
		supertone.setOutputFormat("mp3");
		properties.setSupertone(supertone);

		// Personas 설정
		Map<String, PersonaVoiceConfig> personas = new HashMap<>();

		PersonaVoiceConfig maidConfig = new PersonaVoiceConfig();
		maidConfig.setVoiceId("2c5f135cb33f49a2c8882d");
		maidConfig.setLanguage("ko");
		maidConfig.setStyle("neutral");
		var maidSettings = new RagDialogueProperties.Supertone.VoiceSettings();
		maidSettings.setPitchShift(0);
		maidSettings.setPitchVariance(1.0);
		maidSettings.setSpeed(1.1);
		maidConfig.setVoiceSettings(maidSettings);
		personas.put("maid", maidConfig);

		PersonaVoiceConfig interviewerConfig = new PersonaVoiceConfig();
		interviewerConfig.setVoiceId("4653d63d07d5340656b6bc");
		interviewerConfig.setLanguage("ko");
		interviewerConfig.setStyle("neutral");
		var interviewerSettings = new RagDialogueProperties.Supertone.VoiceSettings();
		interviewerSettings.setPitchShift(0);
		interviewerSettings.setPitchVariance(1.0);
		interviewerSettings.setSpeed(1.0);
		interviewerConfig.setVoiceSettings(interviewerSettings);
		personas.put("interviewer", interviewerConfig);

		properties.setPersonas(personas);

		voiceProvider = new PersonaVoiceProvider(properties, defaultVoice);
	}

	@Test
	@DisplayName("null PersonaId에 대해 기본 Voice를 반환한다")
	void testNullPersonaId() {
		Voice result = voiceProvider.getVoiceForPersona(null);
		assertThat(result).isEqualTo(defaultVoice);
	}

	@Test
	@DisplayName("DEFAULT PersonaId에 대해 기본 Voice를 반환한다")
	void testDefaultPersonaId() {
		Voice result = voiceProvider.getVoiceForPersona(PersonaId.DEFAULT);
		assertThat(result).isEqualTo(defaultVoice);
	}

	@Test
	@DisplayName("maid 페르소나에 대해 올바른 Voice를 반환한다")
	void testMaidPersona() {
		Voice result = voiceProvider.getVoiceForPersona(PersonaId.of("maid"));

		assertThat(result.getId()).isEqualTo("2c5f135cb33f49a2c8882d");
		assertThat(result.getName()).isEqualTo("maid");
		assertThat(result.getProvider()).isEqualTo("supertone");
		assertThat(result.getLanguage()).isEqualTo("ko");
		assertThat(result.getStyle()).isEqualTo(VoiceStyle.NEUTRAL);
		assertThat(result.getSettings().speed()).isEqualTo(1.1);
	}

	@Test
	@DisplayName("interviewer 페르소나에 대해 올바른 Voice를 반환한다")
	void testInterviewerPersona() {
		Voice result = voiceProvider.getVoiceForPersona(PersonaId.of("interviewer"));

		assertThat(result.getId()).isEqualTo("4653d63d07d5340656b6bc");
		assertThat(result.getName()).isEqualTo("interviewer");
		assertThat(result.getProvider()).isEqualTo("supertone");
		assertThat(result.getLanguage()).isEqualTo("ko");
		assertThat(result.getStyle()).isEqualTo(VoiceStyle.NEUTRAL);
		assertThat(result.getSettings().speed()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("설정되지 않은 페르소나에 대해 기본 Voice를 반환한다")
	void testUnknownPersona() {
		Voice result = voiceProvider.getVoiceForPersona(PersonaId.of("unknown"));
		assertThat(result).isEqualTo(defaultVoice);
	}

	@Test
	@DisplayName("각 페르소나마다 다른 Voice 객체를 반환한다")
	void testDifferentVoicesForDifferentPersonas() {
		Voice maidVoice = voiceProvider.getVoiceForPersona(PersonaId.of("maid"));
		Voice interviewerVoice = voiceProvider.getVoiceForPersona(PersonaId.of("interviewer"));

		assertThat(maidVoice.getId()).isNotEqualTo(interviewerVoice.getId());
		assertThat(maidVoice.getSettings().speed()).isNotEqualTo(
			interviewerVoice.getSettings().speed());
	}
}
