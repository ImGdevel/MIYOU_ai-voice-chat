package com.miyou.app.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.port.ConversationRepository;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.memory.port.MemoryRetrievalPort;
import com.miyou.app.domain.retrieval.model.RetrievalContext;
import com.miyou.app.domain.retrieval.port.RetrievalPort;
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
	public Mono<RetrievalContext> retrieve(ConversationSessionId sessionId,
		String query,
		int topK) {
		return conversationRepository.findRecent(sessionId, topK * 10)
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK))
			.map(documents -> RetrievalContext.of(query, documents));
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK) {
		return memoryRetrievalPort.retrieveMemories(sessionId, query, topK)
			.onErrorResume(error -> {
				log.warn("Memory retrieval failed for query '{}': {}",
					query,
					error.getMessage(),
					error);
				return Mono.just(MemoryRetrievalResult.empty());
			});
	}
}
