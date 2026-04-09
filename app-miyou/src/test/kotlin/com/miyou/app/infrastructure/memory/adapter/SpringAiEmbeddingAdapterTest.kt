package com.miyou.app.infrastructure.memory.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.EmbeddingResponseMetadata
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class SpringAiEmbeddingAdapterTest {
    @Mock
    private lateinit var embeddingModel: EmbeddingModel

    private lateinit var embeddingAdapter: SpringAiEmbeddingAdapter

    @BeforeEach
    fun setUp() {
        embeddingAdapter = SpringAiEmbeddingAdapter(embeddingModel)
    }

    @Test
    @DisplayName("임베딩 응답을 MemoryEmbedding으로 변환한다")
    fun embed_mapsResponseIntoMemoryEmbedding() {
        val inputText = "test text"
        val response =
            EmbeddingResponse(
                listOf(Embedding(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f), 0)),
                EmbeddingResponseMetadata(),
            )

        `when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

        StepVerifier
            .create(embeddingAdapter.embed(inputText))
            .assertNext { result ->
                assertThat(result.text).isEqualTo(inputText)
                assertThat(result.vector).hasSize(5)
                assertThat(result.vector[0]).isEqualTo(0.1f)
                assertThat(result.vector[4]).isEqualTo(0.5f)
            }.verifyComplete()
    }

    @Test
    @DisplayName("임베딩 모델이 결과를 주지 않으면 실패한다")
    fun embed_failsWhenNoEmbeddingIsReturned() {
        val response = EmbeddingResponse(emptyList(), EmbeddingResponseMetadata())

        `when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

        StepVerifier
            .create(embeddingAdapter.embed("test text"))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    @DisplayName("큰 벡터도 손실 없이 유지한다")
    fun embed_keepsLargeVectorsIntact() {
        val inputText = "long text"
        val vector = FloatArray(1536) { index -> index * 0.001f }
        val response =
            EmbeddingResponse(
                listOf(Embedding(vector, 0)),
                EmbeddingResponseMetadata(),
            )

        `when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

        StepVerifier
            .create(embeddingAdapter.embed(inputText))
            .assertNext { result ->
                assertThat(result.text).isEqualTo(inputText)
                assertThat(result.vector).hasSize(1536)
                assertThat(result.vector[0]).isEqualTo(0.0f)
                assertThat(result.vector[1535]).isCloseTo(1.535f, within(0.0001f))
            }.verifyComplete()
    }
}
