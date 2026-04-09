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
	@DisplayName("텍스트를 임베딩 벡터로 변환한다")
	fun embed_success() {
		val inputText = "테스트 텍스트"
		val mockEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
		val embedding = Embedding(mockEmbedding, 0)
		val response = EmbeddingResponse(listOf(embedding), EmbeddingResponseMetadata())

		`when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.assertNext { result ->
				assertThat(result.text()).isEqualTo(inputText)
				assertThat(result.vector()).hasSize(5)
				assertThat(result.vector()[0]).isEqualTo(0.1f)
				assertThat(result.vector()[4]).isEqualTo(0.5f)
			}
			.verifyComplete()
	}

	@Test
	@DisplayName("임베딩 결과가 비어 있으면 예외를 발생시킨다")
	fun embed_emptyResult_throwsException() {
		val response = EmbeddingResponse(emptyList(), EmbeddingResponseMetadata())
		`when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

		StepVerifier.create(embeddingAdapter.embed("테스트 텍스트"))
			.expectErrorMatches { throwable ->
				throwable is RuntimeException && throwable.message!!.contains("임베딩 생성에 실패했습니다")
			}
			.verify()
	}

	@Test
	@DisplayName("1536차원 벡터를 올바르게 변환한다")
	fun embed_largeVector_success() {
		val inputText = "긴 텍스트"
		val mockEmbedding = FloatArray(1536) { index -> index * 0.001f }
		val response = EmbeddingResponse(listOf(Embedding(mockEmbedding, 0)), EmbeddingResponseMetadata())

		`when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.assertNext { result ->
				assertThat(result.text()).isEqualTo(inputText)
				assertThat(result.vector()).hasSize(1536)
				assertThat(result.vector()[0]).isEqualTo(0.0f)
				assertThat(result.vector()[1535]).isCloseTo(1.535f, within(0.0001f))
			}
			.verifyComplete()
	}

	@Test
	@DisplayName("다양한 텍스트 길이에 대해 임베딩을 생성한다")
	fun embed_variousTextLengths_success() {
		val testTexts = listOf(
			"짧은 텍스트",
			"조금 더 긴 텍스트입니다. 여러 문장으로 구성되어 있습니다.",
			"매우 긴 텍스트입니다. 이 텍스트는 여러 문장으로 구성되어 있으며 다양한 내용을 포함하고 있습니다. 임베딩 모델이 이를 올바르게 처리하는지 테스트합니다.",
		)
		val response = EmbeddingResponse(
			listOf(Embedding(floatArrayOf(0.1f, 0.2f, 0.3f), 0)),
			EmbeddingResponseMetadata(),
		)

		for (text in testTexts) {
			`when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

			StepVerifier.create(embeddingAdapter.embed(text))
				.assertNext { result ->
					assertThat(result.text()).isEqualTo(text)
					assertThat(result.vector()).hasSize(3)
				}
				.verifyComplete()
		}
	}

	@Test
	@DisplayName("MemoryEmbedding의 vector는 List 형태로 반환된다")
	fun embed_returnsImmutableList() {
		val response = EmbeddingResponse(
			listOf(Embedding(floatArrayOf(0.1f, 0.2f, 0.3f), 0)),
			EmbeddingResponseMetadata(),
		)
		`when`(embeddingModel.call(any(EmbeddingRequest::class.java))).thenReturn(response)

		StepVerifier.create(embeddingAdapter.embed("테스트"))
			.assertNext { result -> assertThat(result.vector()).isInstanceOf(List::class.java) }
			.verifyComplete()
	}
}
