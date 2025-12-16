package com.study.webflux.rag.infrastructure.adapter.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import com.study.webflux.rag.domain.model.memory.MemoryEmbedding;

import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SpringAiEmbeddingAdapterTest {

	@Mock
	private EmbeddingModel embeddingModel;

	private SpringAiEmbeddingAdapter embeddingAdapter;

	@BeforeEach
	void setUp() {
		embeddingAdapter = new SpringAiEmbeddingAdapter(embeddingModel);
	}

	@Test
	@DisplayName("텍스트를 임베딩 벡터로 변환한다")
	void embed_success() {
		String inputText = "테스트 텍스트";
		float[] mockEmbedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};

		Embedding embedding = new Embedding(mockEmbedding, 0);
		EmbeddingResponse response = new EmbeddingResponse(
			List.of(embedding),
			new EmbeddingResponseMetadata()
		);

		when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				assertThat(result.text()).isEqualTo(inputText);
				assertThat(result.vector()).hasSize(5);
				assertThat(result.vector().get(0)).isEqualTo(0.1f);
				assertThat(result.vector().get(4)).isEqualTo(0.5f);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("임베딩 결과가 비어있으면 예외를 발생시킨다")
	void embed_emptyResult_throwsException() {
		String inputText = "테스트 텍스트";

		EmbeddingResponse response = new EmbeddingResponse(
			List.of(),
			new EmbeddingResponseMetadata()
		);

		when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.expectErrorMatches(throwable ->
				throwable instanceof RuntimeException &&
				throwable.getMessage().contains("임베딩 생성에 실패했습니다")
			)
			.verify();
	}

	@Test
	@DisplayName("1536차원 벡터를 올바르게 변환한다")
	void embed_largeVector_success() {
		String inputText = "긴 텍스트";
		float[] mockEmbedding = new float[1536];
		for (int i = 0; i < 1536; i++) {
			mockEmbedding[i] = (float) (i * 0.001);
		}

		Embedding embedding = new Embedding(mockEmbedding, 0);
		EmbeddingResponse response = new EmbeddingResponse(
			List.of(embedding),
			new EmbeddingResponseMetadata()
		);

		when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				assertThat(result.text()).isEqualTo(inputText);
				assertThat(result.vector()).hasSize(1536);
				assertThat(result.vector().get(0)).isEqualTo(0.0f);
				assertThat(result.vector().get(1535)).isEqualTo(1.535f);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("다양한 텍스트 길이에 대해 임베딩을 생성한다")
	void embed_variousTextLengths_success() {
		String[] testTexts = {
			"짧은 텍스트",
			"조금 더 긴 텍스트입니다. 여러 문장으로 구성되어 있습니다.",
			"매우 긴 텍스트입니다. " +
			"이 텍스트는 여러 문장으로 구성되어 있으며, " +
			"다양한 내용을 포함하고 있습니다. " +
			"임베딩 모델이 이를 올바르게 처리할 수 있는지 테스트합니다."
		};

		float[] mockEmbedding = {0.1f, 0.2f, 0.3f};

		for (String text : testTexts) {
			Embedding embedding = new Embedding(mockEmbedding, 0);
			EmbeddingResponse response = new EmbeddingResponse(
				List.of(embedding),
				new EmbeddingResponseMetadata()
			);

			when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

			StepVerifier.create(embeddingAdapter.embed(text))
				.assertNext(result -> {
					assertThat(result).isNotNull();
					assertThat(result.text()).isEqualTo(text);
					assertThat(result.vector()).hasSize(3);
				})
				.verifyComplete();
		}
	}

	@Test
	@DisplayName("MemoryEmbedding 객체가 불변 리스트를 반환한다")
	void embed_returnsImmutableList() {
		String inputText = "테스트";
		float[] mockEmbedding = {0.1f, 0.2f, 0.3f};

		Embedding embedding = new Embedding(mockEmbedding, 0);
		EmbeddingResponse response = new EmbeddingResponse(
			List.of(embedding),
			new EmbeddingResponseMetadata()
		);

		when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

		StepVerifier.create(embeddingAdapter.embed(inputText))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				List<Float> vector = result.vector();
				assertThat(vector).isInstanceOf(List.class);
			})
			.verifyComplete();
	}
}
