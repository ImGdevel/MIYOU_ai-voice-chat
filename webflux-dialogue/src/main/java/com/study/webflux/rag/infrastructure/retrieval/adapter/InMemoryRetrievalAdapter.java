package com.study.webflux.rag.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemorySearchQuery;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalQuery;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

/** 키워드 유사도 기반 인메모리 대화 검색 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class InMemoryRetrievalAdapter implements RetrievalPort {

	private final ConversationRepository conversationRepository;

	@Override
	public Mono<RetrievalContext> retrieve(RetrievalQuery query) {
		return conversationRepository.findAll(query.sessionId())
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query.query(),
				turns,
				query.topK()))
			.map(documents -> RetrievalContext.of(query.query(), documents));
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(MemorySearchQuery query) {
		return Mono.just(MemoryRetrievalResult.empty());
	}
}
