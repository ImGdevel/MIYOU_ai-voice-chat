package com.miyou.app.domain.voice.port

import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.voice.model.Voice

interface VoiceSelectionPort {
    fun getVoiceForPersona(personaId: PersonaId): Voice
}
