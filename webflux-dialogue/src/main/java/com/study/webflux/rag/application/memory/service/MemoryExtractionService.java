package com.study.webflux.rag.application.memory.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.ExtractedMemory;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryExtractionContext;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.domain.memory.port.EmbeddingPort;
import com.study.webflux.rag.domain.memory.port.MemoryExtractionPort;
import com.study.webflux.rag.domain.memory.port.VectorMemoryPort;
import reactor.core.publisher.Mono;

/**
 * 대화 이력과 기존 메모리를 바탕으로 신규 메모리를 추출/저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

	private final ConversationRepository conversationRepository;
	private final ConversationCounterPort counterPort;
	private final MemoryExtractionPort extractionPort;
	private final EmbeddingPort embeddingPort;
	private final VectorMemoryPort vectorMemoryPort;
	private final MemoryRetrievalService retrievalService;
	private final int conversationThreshold;

	/**
	 * 누적 대화 수가 임계값 배수인 경우에만 메모리 추출 파이프라인을 실행합니다.
	 */
	public Mono<Void> checkAndExtract(UserId userId) {
		return counterPort.get(userId)
			.filter(this::isExtractionTurn)
			.flatMap(count -> {
				log.info("메모리 추출 트리거: 대화 횟수={}", count);
				return performExtraction(userId);
			})
			.then();
	}

	/**
	 * 최근 대화를 기준으로 메모리 추출 컨텍스트를 구성하고 추출 결과를 저장합니다.
	 */
	private Mono<Void> performExtraction(UserId userId) {
		return loadRecentConversations(userId)
			.flatMap(conversations -> buildExtractionContext(userId, conversations))
			.flatMapMany(extractionPort::extractMemories)
			.flatMap(this::saveExtractedMemory)
			.doOnNext(memory -> log.info("추출 및 저장된 메모리: type={}, importance={}, content={}",
				memory.type(),
				memory.importance(),
				memory.content()))
			.then();
	}

	/**
	 * 추출된 메모리를 임베딩한 뒤 벡터 저장소에 저장합니다.
	 */
	private Mono<Memory> saveExtractedMemory(ExtractedMemory extracted) {
		Memory memory = extracted.toMemory();

		return embeddingPort.embed(memory.content())
			.flatMap(embedding -> vectorMemoryPort.upsert(memory, embedding.vector()));
	}

	private boolean isExtractionTurn(long count) {
		return count > 0 && count % conversationThreshold == 0;
	}

	/**
	 * 임계 대화 수만큼 최근 대화 이력을 불러옵니다.
	 */
	private Mono<List<ConversationTurn>> loadRecentConversations(UserId userId) {
		return conversationRepository.findRecent(userId, conversationThreshold).collectList();
	}

	/**
	 * 대화 이력과 관련 메모리를 결합해 추출 컨텍스트를 구성합니다.
	 */
	private Mono<MemoryExtractionContext> buildExtractionContext(UserId userId,
		List<ConversationTurn> conversations) {
		String combinedQuery = mergeQueries(conversations);
		return retrievalService.retrieveMemories(userId, combinedQuery, 10)
			.map(result -> MemoryExtractionContext.of(userId, conversations, result.allMemories()));
	}

	private String mergeQueries(List<ConversationTurn> conversations) {
		return conversations.stream().map(ConversationTurn::query).reduce((a, b) -> a + " " + b)
			.orElse("");
	}
}
