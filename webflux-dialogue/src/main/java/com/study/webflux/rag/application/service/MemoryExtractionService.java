package com.study.webflux.rag.application.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.model.conversation.ConversationTurn;
import com.study.webflux.rag.domain.model.memory.ExtractedMemory;
import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryExtractionContext;
import com.study.webflux.rag.domain.port.out.ConversationCounterPort;
import com.study.webflux.rag.domain.port.out.ConversationRepository;
import com.study.webflux.rag.domain.port.out.EmbeddingPort;
import com.study.webflux.rag.domain.port.out.MemoryExtractionPort;
import com.study.webflux.rag.domain.port.out.VectorMemoryPort;
import reactor.core.publisher.Mono;

/**
 * MemoryExtractionService는 메모리 추출 작업을 수행하는 서비스를 정의합니다.
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
	 * 메모리 추출 작업을 트리거합니다.
	 *
	 * @return Mono<Void> 메모리 추출 작업 결과
	 */
	public Mono<Void> checkAndExtract() {
		return counterPort.get().filter(count -> count > 0 && count % conversationThreshold == 0)
			.flatMap(count -> {
				log.info("메모리 추출 트리거: 대화 횟수={}", count);
				return performExtraction();
			}).then();
	}

	/**
	 * 메모리 추출 작업을 수행합니다.
	 *
	 * @return Mono<Void> 메모리 추출 작업 결과
	 */
	private Mono<Void> performExtraction() {
		Mono<List<ConversationTurn>> recentConversations = conversationRepository
			.findRecent(conversationThreshold).collectList();

		return recentConversations.flatMap(conversations -> {
			String combinedQuery = conversations.stream().map(ConversationTurn::query)
				.reduce((a, b) -> a + " " + b).orElse("");

			return retrievalService.retrieveMemories(combinedQuery, 10)
				.map(result -> MemoryExtractionContext.of(conversations, result.allMemories()));
		}).flatMapMany(extractionPort::extractMemories).flatMap(this::saveExtractedMemory)
			.doOnNext(memory -> log.info("추출 및 저장된 메모리: type={}, importance={}, content={}",
				memory.type(),
				memory.importance(),
				memory.content()))
			.then();
	}

	/**
	 * 추출된 메모리를 저장합니다.
	 *
	 * @param extracted
	 *            추출된 메모리
	 * @return Mono<Memory> 저장된 메모리
	 */
	private Mono<Memory> saveExtractedMemory(ExtractedMemory extracted) {
		Memory memory = extracted.toMemory();

		return embeddingPort.embed(memory.content())
			.flatMap(embedding -> vectorMemoryPort.upsert(memory, embedding.vector()));
	}
}
