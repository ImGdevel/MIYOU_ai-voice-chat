package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.CompletionRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface LlmPort {
    fun streamCompletion(request: CompletionRequest): Flux<String>

    fun complete(request: CompletionRequest): Mono<String>
}
