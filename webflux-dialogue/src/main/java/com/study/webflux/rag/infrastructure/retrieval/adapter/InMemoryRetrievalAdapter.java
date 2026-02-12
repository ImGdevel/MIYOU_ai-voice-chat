package com.study.webflux.rag.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

/** 키워드 유사도 기반 인메모리 대화 검색 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class InMemoryRetrievalAdapter implements RetrievalPort {

	private final ConversationRepository conversationRepository;

	/** 전체 대화를 메모리에서 로드하고 키워드 유사도로 문서를 검색합니다. */
	@Override
	public Mono<RetrievalContext> retrieve(String query, int topK) {
		return conversationRepository.findAll()
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK))
			.map(documents -> RetrievalContext.of(query, documents));
	}

	/** 메모리 검색은 지원하지 않습니다. */
	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(String query, int topK) {
		return Mono.just(MemoryRetrievalResult.empty());
	}
}
