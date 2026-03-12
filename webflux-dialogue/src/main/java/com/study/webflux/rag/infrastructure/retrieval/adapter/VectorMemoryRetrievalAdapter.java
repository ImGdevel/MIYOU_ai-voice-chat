package com.study.webflux.rag.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemorySearchQuery;
import com.study.webflux.rag.domain.memory.port.MemoryRetrievalPort;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalQuery;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Primary
@Slf4j
public class VectorMemoryRetrievalAdapter implements RetrievalPort {

	private final MemoryRetrievalPort memoryRetrievalPort;
	private final ConversationRepository conversationRepository;

	/**
	 * 최근 대화 이력을 대상으로 키워드 유사도 검색을 수행해 검색 컨텍스트를 생성합니다.
	 */
	@Override
	public Mono<RetrievalContext> retrieve(RetrievalQuery query) {
		return conversationRepository.findRecent(query.sessionId(), query.topK() * 10)
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query.query(),
				turns,
				query.topK()))
			.map(documents -> RetrievalContext.of(query.query(), documents));
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(MemorySearchQuery query) {
		return memoryRetrievalPort.retrieveMemories(query)
			.onErrorResume(error -> {
				log.warn("Memory retrieval failed for query '{}': {}",
					query.query(),
					error.getMessage(),
					error);
				return Mono.just(MemoryRetrievalResult.empty());
			});
	}
}
