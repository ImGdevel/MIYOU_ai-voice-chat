package com.study.webflux.rag.infrastructure.adapter.embedding;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.model.memory.MemoryEmbedding;
import com.study.webflux.rag.domain.port.out.EmbeddingPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Primary
@Component
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

	private final EmbeddingModel embeddingModel;

	public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	@Override
	public Mono<MemoryEmbedding> embed(String text) {
		return Mono.fromCallable(() -> {
			EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
			EmbeddingResponse response = embeddingModel.call(request);

			if (response.getResults().isEmpty()) {
				throw new RuntimeException("임베딩 생성에 실패했습니다");
			}

			float[] floatArray = response.getResults().get(0).getOutput();
			List<Float> floatVector = new java.util.ArrayList<>();
			for (float f : floatArray) {
				floatVector.add(f);
			}

			return MemoryEmbedding.of(text, floatVector);
		}).subscribeOn(Schedulers.boundedElastic());
	}
}
