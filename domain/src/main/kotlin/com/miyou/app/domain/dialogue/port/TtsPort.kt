package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.TtsCommand
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TtsPort {
    fun streamSynthesize(command: TtsCommand): Flux<ByteArray>

    fun synthesize(command: TtsCommand): Mono<ByteArray>

    fun prepare(): Mono<Void> = Mono.empty()
}
