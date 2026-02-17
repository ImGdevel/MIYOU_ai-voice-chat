package com.study.webflux.rag.application.memory.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
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

	/**
	 * 누적 대화 수가 임계값 배수인 경우에만 메모리 추출 파이프라인을 실행합니다.
	 */
	public Mono<Void> checkAndExtract(PersonaId personaId, UserId userId) {
		return counterPort.get(personaId, userId)
			.filter(this::isExtractionTurn)
			.flatMap(count -> {
				log.info("메모리 추출 트리거: persona={}, 대화 횟수={}", personaId.value(), count);
				extractionMetrics.recordExtractionTriggered();
				return performExtraction(personaId, userId);
			})
			.then();
	}

	/**
	 * 하위 호환성을 위한 기존 메서드
	 */
	public Mono<Void> checkAndExtract(UserId userId) {
		return checkAndExtract(PersonaId.defaultPersona(), userId);
	}

	/**
	 * 최근 대화를 기준으로 메모리 추출 컨텍스트를 구성하고 추출 결과를 저장합니다.
	 */
	private Mono<Void> performExtraction(PersonaId personaId, UserId userId) {
		return loadRecentConversations(personaId, userId)
			.flatMap(conversations -> buildExtractionContext(personaId, userId, conversations))
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
	private Mono<List<ConversationTurn>> loadRecentConversations(PersonaId personaId,
		UserId userId) {
		return conversationRepository.findRecent(personaId, userId, conversationThreshold)
			.collectList();
	}

	/**
	 * 대화 이력과 관련 메모리를 결합해 추출 컨텍스트를 구성합니다.
	 */
	private Mono<MemoryExtractionContext> buildExtractionContext(PersonaId personaId,
		UserId userId,
		List<ConversationTurn> conversations) {
		String combinedQuery = mergeQueries(conversations);
		return retrievalService.retrieveMemories(personaId, userId, combinedQuery, 10)
			.map(result -> MemoryExtractionContext
				.of(personaId, userId, conversations, result.allMemories()));
	}

	private String mergeQueries(List<ConversationTurn> conversations) {
		return conversations.stream().map(ConversationTurn::query).reduce((a, b) -> a + " " + b)
			.orElse("");
	}
}
