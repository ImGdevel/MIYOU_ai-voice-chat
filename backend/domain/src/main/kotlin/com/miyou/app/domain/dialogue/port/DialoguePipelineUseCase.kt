package com.miyou.app.domain.dialogue.port

import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.ConversationSession
import reactor.core.publisher.Flux

interface DialoguePipelineUseCase {
    fun executeAudioStreaming(
        session: ConversationSession,
        text: String,
        format: AudioFormat = AudioFormat.WAV,
    ): Flux<ByteArray>

    fun executeTextOnly(
        session: ConversationSession,
        text: String,
    ): Flux<String>
}
