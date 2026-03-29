package com.miyou.app.infrastructure.retrieval.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.port.ConversationRepository;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.retrieval.model.RetrievalContext;
import com.miyou.app.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

/** 키워드 유사도 기반 인메모리 대화 검색 어댑터입니다. */
@Component
@RequiredArgsConstructor
public class InMemoryRetrievalAdapter implements RetrievalPort {

	private final ConversationRepository conversationRepository;

	@Override
	public Mono<RetrievalContext> retrieve(ConversationSessionId sessionId,
		String query,
		int topK) {
		return conversationRepository.findRecent(sessionId, topK)
			.collectList()
			.map(turns -> KeywordSimilaritySupport.rankDocumentsByQuery(query, turns, topK))
			.map(documents -> RetrievalContext.of(query, documents));
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK) {
		return Mono.just(MemoryRetrievalResult.empty());
	}
}
