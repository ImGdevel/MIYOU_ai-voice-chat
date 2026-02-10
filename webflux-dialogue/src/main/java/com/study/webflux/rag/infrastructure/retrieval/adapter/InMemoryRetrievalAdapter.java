package com.study.webflux.rag.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class InMemoryRetrievalAdapter implements RetrievalPort {

	private final ConversationRepository conversationRepository;

	/**
	 * 저장된 대화 이력을 대상으로 키워드 유사도 기반 상위 문서를 검색합니다.
	 */
	@Override
	public Mono<RetrievalContext> retrieve(String query, int topK) {
		return conversationRepository.findAll()
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK))
			.map(documents -> RetrievalContext.of(query, documents));
	}

	/**
	 * 인메모리 어댑터는 메모리 검색을 지원하지 않아 빈 결과를 반환합니다.
	 */
	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(String query, int topK) {
		return Mono.just(MemoryRetrievalResult.empty());
	}
}
