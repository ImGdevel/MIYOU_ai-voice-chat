package com.miyou.app.domain.voice.port

import com.miyou.app.domain.voice.model.Voice

interface VoiceSelectionPort {
    fun getVoiceForPersona(personaId: String): Voice
}
