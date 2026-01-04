package com.study.webflux.rag.infrastructure.dialogue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.model.Voice;
import com.study.webflux.rag.domain.voice.model.VoiceSettings;
import com.study.webflux.rag.domain.voice.model.VoiceStyle;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;

@Configuration
public class DialogueVoiceConfiguration {

	@Bean
	public Voice defaultVoice(RagDialogueProperties properties) {
		var supertone = properties.getSupertone();
		var settings = supertone.getVoiceSettings();

		return Voice.builder().id(supertone.getVoiceId()).name("adam").provider("supertone")
			.settings(new VoiceSettings(settings.getPitchShift(), settings.getPitchVariance(),
				settings.getSpeed()))
			.language(supertone.getLanguage()).style(VoiceStyle.fromString(supertone.getStyle()))
			.outputFormat(AudioFormat.fromString(supertone.getOutputFormat())).build();
	}
}
