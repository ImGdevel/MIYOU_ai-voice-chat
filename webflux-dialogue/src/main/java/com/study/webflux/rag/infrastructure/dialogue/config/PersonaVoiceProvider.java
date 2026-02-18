package com.study.webflux.rag.infrastructure.dialogue.config;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.domain.voice.model.VoiceSettings;
import com.study.webflux.rag.domain.voice.model.VoiceStyle;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties.PersonaVoiceConfig;

/**
 * 페르소나별 TTS Voice를 제공하는 서비스. application.yml의 personas 설정을 기반으로 각 페르소나에 맞는 Voice 객체를 반환합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaVoiceProvider {

	private final RagDialogueProperties properties;
	private final Voice defaultVoice;

	/**
	 * 주어진 페르소나에 해당하는 Voice를 반환합니다. 설정이 없는 페르소나는 기본 Voice를 반환합니다.
	 *
	 * @param personaId
	 *            페르소나 식별자
	 * @return 페르소나에 맞는 Voice 객체
	 */
	public Voice getVoiceForPersona(PersonaId personaId) {
		if (personaId == null || PersonaId.DEFAULT.equals(personaId)) {
			return defaultVoice;
		}

		String personaKey = personaId.value();
		Map<String, PersonaVoiceConfig> personas = properties.getPersonas();

		if (personas.containsKey(personaKey)) {
			PersonaVoiceConfig config = personas.get(personaKey);
			return buildVoiceFromConfig(personaKey, config);
		}

		log.warn("페르소나 '{}'에 대한 보이스 설정이 없어 기본 보이스 사용", personaKey);
		return defaultVoice;
	}

	/**
	 * PersonaVoiceConfig를 Voice 도메인 객체로 변환합니다.
	 */
	private Voice buildVoiceFromConfig(String personaKey, PersonaVoiceConfig config) {
		var voiceSettingsConfig = config.getVoiceSettings();
		VoiceSettings settings = new VoiceSettings(voiceSettingsConfig.getPitchShift(),
			voiceSettingsConfig.getPitchVariance(), voiceSettingsConfig.getSpeed());

		return Voice.builder().id(config.getVoiceId()).name(personaKey).provider("supertone")
			.settings(settings).language(config.getLanguage())
			.style(VoiceStyle.fromString(config.getStyle()))
			.outputFormat(AudioFormat.fromString(properties.getSupertone().getOutputFormat()))
			.build();
	}
}
