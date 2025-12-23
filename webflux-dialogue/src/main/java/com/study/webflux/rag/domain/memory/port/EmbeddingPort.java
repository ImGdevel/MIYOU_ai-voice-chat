package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.memory.model.MemoryEmbedding;
import reactor.core.publisher.Mono;

/**
 * EmbeddingPort는 텍스트 데이터를 벡터 임베딩으로 변환하는 역할을 담당합니다.
 */
public interface EmbeddingPort {

	/**
	 * 텍스트를 벡터 임베딩으로 변환합니다.
	 *
	 * @param text
	 *            변환할 텍스트
	 * @return 벡터 임베딩을 포함하는 MemoryEmbedding 객체
	 */
	Mono<MemoryEmbedding> embed(String text);
}
