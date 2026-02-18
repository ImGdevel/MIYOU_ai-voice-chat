package com.study.webflux.rag.fixture;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

public final class PipelineInputsFixture {

	private PipelineInputsFixture() {
	}

	public static PipelineInputs create() {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationTurn turn = ConversationTurnFixture.create(session.sessionId());
		return new PipelineInputs(
			session,
			RetrievalContext.empty(turn.query()),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs create(ConversationSession session) {
		ConversationTurn turn = ConversationTurnFixture.create(session.sessionId());
		return new PipelineInputs(
			session,
			RetrievalContext.empty(turn.query()),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs createWithQuery(String query) {
		ConversationSession session = ConversationSessionFixture.create();
		ConversationTurn turn = ConversationTurn.create(session.sessionId(), query);
		return new PipelineInputs(
			session,
			RetrievalContext.empty(query),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs createMinimal(ConversationSession session,
		ConversationTurn turn) {
		return new PipelineInputs(session, null, null, null, turn);
	}
}
