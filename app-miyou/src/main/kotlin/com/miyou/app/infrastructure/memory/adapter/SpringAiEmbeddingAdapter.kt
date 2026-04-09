package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.memory.model.MemoryEmbedding
import com.miyou.app.domain.memory.port.EmbeddingPort
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Primary
@Component
class SpringAiEmbeddingAdapter(
    private val embeddingModel: EmbeddingModel,
) : EmbeddingPort {
    override fun embed(text: String): Mono<MemoryEmbedding> =
        Mono
            .fromCallable {
                val request = EmbeddingRequest(listOf(text), null)
                val response: EmbeddingResponse = embeddingModel.call(request)

                if (response.results.isEmpty()) {
                    throw RuntimeException("임베딩 생성에 실패했습니다")
                }

                val floatArray = response.results.first().output
                val floatVector = floatArray.map { it.toFloat() }
                MemoryEmbedding.of(text, floatVector)
            }.subscribeOn(Schedulers.boundedElastic())
}
