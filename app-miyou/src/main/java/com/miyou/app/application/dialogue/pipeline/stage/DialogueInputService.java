package com.miyou.app.application.dialogue.pipeline.stage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.miyou.app.application.dialogue.pipeline.PipelineInputs;
import com.miyou.app.application.monitoring.service.PipelineTracer;
import com.miyou.app.domain.dialogue.model.ConversationContext;
import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.domain.dialogue.port.ConversationRepository;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.retrieval.model.RetrievalContext;
import com.miyou.app.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DialogueInputService {

	private final RetrievalPort retrievalPort;
	private final ConversationRepository conversationRepository;
	private final PipelineTracer pipelineTracer;

	public Mono<PipelineInputs> prepareInputs(ConversationSession session, String text) {
		Mono<ConversationTurn> currentTurn = Mono
			.fromCallable(() -> ConversationTurn.create(session.sessionId(), text))
			.cache();

		Mono<MemoryRetrievalResult> memories = pipelineTracer.traceMemories(
			() -> retrievalPort.retrieveMemories(session.sessionId(), text, 5));

		Mono<RetrievalContext> retrievalContext = pipelineTracer.traceRetrieval(
			() -> retrievalPort.retrieve(session.sessionId(), text, 3));

		Mono<ConversationContext> history = loadConversationHistory(session).cache();

		return Mono.zip(retrievalContext, memories, history, currentTurn)
			.map(tuple -> new PipelineInputs(
				session,
				tuple.getT1(),
				tuple.getT2(),
				tuple.getT3(),
				tuple.getT4()));
	}

	private Mono<ConversationContext> loadConversationHistory(ConversationSession session) {
		return conversationRepository.findRecent(session.sessionId(), 10)
			.collectList()
			.map(ConversationContext::of)
			.defaultIfEmpty(ConversationContext.empty());
	}
}
