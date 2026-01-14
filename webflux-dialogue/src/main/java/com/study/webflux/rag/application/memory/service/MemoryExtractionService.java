package com.study.webflux.rag.application.memory.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.ExtractedMemory;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryExtractionContext;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.domain.memory.port.EmbeddingPort;
import com.study.webflux.rag.domain.memory.port.MemoryExtractionPort;
import com.study.webflux.rag.domain.memory.port.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.monitoring.config.MemoryExtractionMetricsConfiguration;
import reactor.core.publisher.Flux;
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
	private final MemoryExtractionMetricsConfiguration extractionMetrics;
	private final int conversationThreshold;

	public Mono<Void> checkAndExtract(ConversationSessionId sessionId) {
		return counterPort.get(sessionId)
			.filter(this::isExtractionTurn)
			.flatMap(count -> {
				log.info("메모리 추출 트리거: sessionId={}, 대화 횟수={}", sessionId.value(), count);
				extractionMetrics.recordExtractionTriggered();
				return performExtraction(sessionId);
			})
			.then();
	}

	private Mono<Void> performExtraction(ConversationSessionId sessionId) {
		return loadRecentConversations(sessionId)
			.flatMap(conversations -> buildExtractionContext(sessionId, conversations))
			.flatMapMany(extractionPort::extractMemories)
			.collectList()
			.doOnNext(extractedList -> {
				extractionMetrics.recordExtractionSuccess(extractedList.size());

				java.util.Map<String, Long> typeCounts = extractedList.stream()
					.collect(java.util.stream.Collectors.groupingBy(
						extracted -> extracted.type().name(),
						java.util.stream.Collectors.counting()));

				typeCounts.forEach((type, count) -> extractionMetrics
					.recordExtractedMemoryType(type, count.intValue()));

				extractedList.forEach(extracted -> extractionMetrics
					.recordExtractedImportance(extracted.importance()));
			})
			.doOnError(error -> {
				extractionMetrics.recordExtractionFailure();
				log.error("메모리 추출 실패", error);
			})
			.flatMapMany(Flux::fromIterable)
			.flatMap(this::saveExtractedMemory)
			.doOnNext(memory -> log.info("추출 및 저장된 메모리: type={}, importance={}, content={}",
				memory.type(),
				memory.importance(),
				memory.content()))
			.then();
	}

	private Mono<Memory> saveExtractedMemory(ExtractedMemory extracted) {
		Memory memory = extracted.toMemory();

		return embeddingPort.embed(memory.content())
			.flatMap(embedding -> vectorMemoryPort.upsert(memory, embedding.vector()));
	}

	private boolean isExtractionTurn(long count) {
		return count > 0 && count % conversationThreshold == 0;
	}

	private Mono<List<ConversationTurn>> loadRecentConversations(ConversationSessionId sessionId) {
		return conversationRepository.findRecent(sessionId, conversationThreshold)
			.collectList();
	}

	private Mono<MemoryExtractionContext> buildExtractionContext(ConversationSessionId sessionId,
		List<ConversationTurn> conversations) {
		String combinedQuery = mergeQueries(conversations);
		return retrievalService.retrieveMemories(sessionId, combinedQuery, 10)
			.map(result -> MemoryExtractionContext
				.of(sessionId, conversations, result.allMemories()));
	}

	private String mergeQueries(List<ConversationTurn> conversations) {
		return conversations.stream().map(ConversationTurn::query).reduce((a, b) -> a + " " + b)
			.orElse("");
	}
}
