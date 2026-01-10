package com.study.webflux.rag.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.application.memory.service.MemoryRetrievalService;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Primary
@Slf4j
public class VectorMemoryRetrievalAdapter implements RetrievalPort {

	private final MemoryRetrievalService memoryRetrievalService;
	private final ConversationRepository conversationRepository;

	/**
	 * 최근 대화 이력을 대상으로 키워드 유사도 검색을 수행해 검색 컨텍스트를 생성합니다.
	 */
	@Override
	public Mono<RetrievalContext> retrieve(UserId userId, String query, int topK) {
		return conversationRepository.findRecent(userId, topK * 10)
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK))
			.map(documents -> RetrievalContext.of(query, documents));
	}

	/**
	 * 벡터 메모리 검색을 수행하며, 실패 시 경고 로그 후 빈 메모리 결과로 폴백합니다.
	 */
	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(UserId userId, String query, int topK) {
		return memoryRetrievalService.retrieveMemories(userId, query, topK)
			.onErrorResume(error -> {
				log.warn("Memory retrieval failed for query '{}': {}",
					query,
					error.getMessage(),
					error);
				return Mono.just(MemoryRetrievalResult.empty());
			});
	}
}
