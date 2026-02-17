package com.study.webflux.rag.application.dialogue.pipeline.stage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DialogueInputService {

	private final RetrievalPort retrievalPort;
	private final ConversationRepository conversationRepository;
	private final PipelineTracer pipelineTracer;

	/**
	 * 현재 질의를 기준으로 검색 컨텍스트, 메모리, 대화 이력, 현재 턴을 병렬로 준비합니다.
	 *
	 * @param personaId
	 *            페르소나 ID
	 * @param userId
	 *            사용자 ID
	 * @param text
	 *            사용자 입력 질의
	 * @return 파이프라인 실행에 필요한 입력 집합
	 */
	public Mono<PipelineInputs> prepareInputs(PersonaId personaId, UserId userId, String text) {
		Mono<ConversationTurn> currentTurn = Mono
			.fromCallable(() -> ConversationTurn.create(personaId, userId, text))
			.cache();

		Mono<MemoryRetrievalResult> memories = pipelineTracer.traceMemories(
			() -> retrievalPort.retrieveMemories(personaId, userId, text, 5));

		Mono<RetrievalContext> retrievalContext = pipelineTracer.traceRetrieval(
			() -> retrievalPort.retrieve(personaId, userId, text, 3));

		Mono<ConversationContext> history = loadConversationHistory(personaId, userId).cache();

		return Mono.zip(retrievalContext, memories, history, currentTurn)
			.map(tuple -> new PipelineInputs(
				personaId,
				userId,
				tuple.getT1(),
				tuple.getT2(),
				tuple.getT3(),
				tuple.getT4()));
	}

	/**
	 * 하위 호환성을 위한 기존 메서드
	 */
	public Mono<PipelineInputs> prepareInputs(UserId userId, String text) {
		return prepareInputs(PersonaId.defaultPersona(), userId, text);
	}

	/**
	 * 최근 대화 이력을 조회해 컨텍스트 객체로 변환합니다.
	 */
	private Mono<ConversationContext> loadConversationHistory(PersonaId personaId, UserId userId) {
		return conversationRepository.findRecent(personaId, userId, 10)
			.collectList()
			.map(ConversationContext::of)
			.defaultIfEmpty(ConversationContext.empty());
	}
}
