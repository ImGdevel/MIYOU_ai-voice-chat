package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TtsPort {
    fun streamSynthesize(
        text: String,
        format: AudioFormat?,
    ): Flux<ByteArray>

    fun streamSynthesize(text: String): Flux<ByteArray> = streamSynthesize(text, null)

    fun streamSynthesize(
        text: String,
        format: AudioFormat?,
        voice: Voice,
    ): Flux<ByteArray>

    fun synthesize(
        text: String,
        format: AudioFormat?,
    ): Mono<ByteArray>

    fun synthesize(text: String): Mono<ByteArray> = synthesize(text, null)

    fun synthesize(
        text: String,
        format: AudioFormat?,
        voice: Voice,
    ): Mono<ByteArray>

    fun prepare(): Mono<Void> = Mono.empty()
}
