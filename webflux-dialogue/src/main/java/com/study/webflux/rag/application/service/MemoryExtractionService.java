package com.study.webflux.rag.application.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.study.webflux.rag.infrastructure.adapter.memory.MemoryExtractionConfig;

import reactor.core.publisher.Mono;

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


	public Mono<Void> checkAndExtract() {
		return counterPort.get()
			.filter(count -> count > 0 && count % conversationThreshold == 0)
			.flatMap(count -> {
				log.info("Triggering memory extraction at conversation count: {}", count);
				return performExtraction();
			})
			.then();
	}

	private Mono<Void> performExtraction() {
		Mono<List<ConversationTurn>> recentConversations =
			conversationRepository.findRecent(conversationThreshold)
				.collectList();

		return recentConversations
			.flatMap(conversations -> {
				String combinedQuery = conversations.stream()
					.map(ConversationTurn::query)
					.reduce((a, b) -> a + " " + b)
					.orElse("");

				return retrievalService.retrieveMemories(combinedQuery, 10)
					.map(result -> MemoryExtractionContext.of(
						conversations,
						result.allMemories()
					));
			})
			.flatMapMany(extractionPort::extractMemories)
			.flatMap(this::saveExtractedMemory)
			.doOnNext(memory -> log.info(
				"Extracted and saved memory: type={}, importance={}, content={}",
				memory.type(),
				memory.importance(),
				memory.content()
			))
			.then();
	}

	private Mono<Memory> saveExtractedMemory(ExtractedMemory extracted) {
		Memory memory = extracted.toMemory();

		return embeddingPort.embed(memory.content())
			.flatMap(embedding -> vectorMemoryPort.upsert(memory, embedding.vector()));
	}
}
