package com.study.webflux.rag.domain.voice.port;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.voice.model.Voice;

public interface VoiceSelectionPort {
	Voice getVoiceForPersona(PersonaId personaId);
}
