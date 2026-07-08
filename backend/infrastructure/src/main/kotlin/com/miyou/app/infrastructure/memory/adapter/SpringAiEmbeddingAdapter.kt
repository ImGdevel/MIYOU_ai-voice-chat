package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.cost.model.ModelPricing
import com.miyou.app.domain.memory.model.MemoryEmbedding
import com.miyou.app.domain.memory.port.EmbeddingPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    private val meterRegistry: MeterRegistry,
) : EmbeddingPort {
    override fun embed(text: String): Mono<MemoryEmbedding> =
        Mono
            .fromCallable {
                val request = EmbeddingRequest(listOf(text), null)
                val response: EmbeddingResponse = embeddingModel.call(request)

                if (response.results.isEmpty()) {
                    throw RuntimeException("임베딩 생성에 실패했습니다")
                }

                recordCostMetrics(response)

                val floatArray = response.results.first().output
                val floatVector = floatArray.map { it.toFloat() }
                MemoryEmbedding.of(text, floatVector)
            }.subscribeOn(Schedulers.boundedElastic())

    private fun recordCostMetrics(response: EmbeddingResponse) {
        val model = response.metadata?.model?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val tokens = response.metadata?.usage?.totalTokens ?: 0L
        if (tokens <= 0L) {
            return
        }
        val credits = ModelPricing.calculateEmbeddingCredits(model, tokens, false)

        Counter
            .builder("embedding.tokens")
            .tag("model", model)
            .description("임베딩 요청 토큰 사용량")
            .register(meterRegistry)
            .increment(tokens.toDouble())

        Counter
            .builder("embedding.cost.credits")
            .tag("model", model)
            .description("임베딩 요청 크레딧 비용")
            .register(meterRegistry)
            .increment(credits.toDouble())
    }

    private companion object {
        const val DEFAULT_MODEL = "text-embedding-3-small"
    }
}
