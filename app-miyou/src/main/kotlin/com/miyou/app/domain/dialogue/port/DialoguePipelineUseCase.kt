package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.voice.model.AudioFormat
import reactor.core.publisher.Flux

interface DialoguePipelineUseCase {
    fun executeAudioStreaming(
        session: ConversationSession,
        text: String,
        format: AudioFormat?,
    ): Flux<ByteArray>

    fun executeTextOnly(
        session: ConversationSession,
        text: String,
    ): Flux<String>
}
