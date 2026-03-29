package com.miyou.app.fixture;

import com.miyou.app.application.dialogue.pipeline.PipelineInputs;
import com.miyou.app.domain.dialogue.model.ConversationContext;
import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.retrieval.model.RetrievalContext;

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
