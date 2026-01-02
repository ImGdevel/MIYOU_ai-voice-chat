package com.study.webflux.rag.application.dialogue.service;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Service
public class DialogueInputService {

	private final RetrievalPort retrievalPort;
	private final ConversationRepository conversationRepository;
	private final PipelineTracer pipelineTracer;

	public DialogueInputService(RetrievalPort retrievalPort,
		ConversationRepository conversationRepository,
		PipelineTracer pipelineTracer) {
		this.retrievalPort = retrievalPort;
		this.conversationRepository = conversationRepository;
		this.pipelineTracer = pipelineTracer;
	}

	public Mono<PipelineInputs> prepareInputs(String text) {
		Mono<ConversationTurn> currentTurn = Mono.fromCallable(() -> ConversationTurn.create(text))
			.cache();

		Mono<MemoryRetrievalResult> memories = pipelineTracer.traceMemories(
			() -> retrievalPort.retrieveMemories(text, 5));

		Mono<RetrievalContext> retrievalContext = pipelineTracer.traceRetrieval(
			() -> retrievalPort.retrieve(text, 3));

		Mono<ConversationContext> history = loadConversationHistory().cache();

		return Mono.zip(retrievalContext, memories, history, currentTurn)
			.map(tuple -> new PipelineInputs(tuple.getT1(), tuple.getT2(), tuple.getT3(),
				tuple.getT4()));
	}

	private Mono<ConversationContext> loadConversationHistory() {
		return conversationRepository.findRecent(10).collectList().map(ConversationContext::of)
			.defaultIfEmpty(ConversationContext.empty());
	}
}
