package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput
import reactor.core.publisher.Mono

interface SttPort {
    fun transcribe(input: AudioTranscriptionInput): Mono<String>
}
