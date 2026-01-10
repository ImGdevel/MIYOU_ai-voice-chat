package com.study.webflux.rag.fixture;

import com.study.webflux.rag.application.dialogue.pipeline.PipelineInputs;
import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

public final class PipelineInputsFixture {

	private PipelineInputsFixture() {
	}

	public static PipelineInputs create() {
		UserId userId = UserIdFixture.create();
		ConversationTurn turn = ConversationTurnFixture.create(userId);
		return new PipelineInputs(
			userId,
			RetrievalContext.empty(turn.query()),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs create(UserId userId) {
		ConversationTurn turn = ConversationTurnFixture.create(userId);
		return new PipelineInputs(
			userId,
			RetrievalContext.empty(turn.query()),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs createWithQuery(String query) {
		UserId userId = UserIdFixture.create();
		ConversationTurn turn = ConversationTurnFixture.create(query);
		return new PipelineInputs(
			userId,
			RetrievalContext.empty(query),
			MemoryRetrievalResult.empty(),
			ConversationContext.empty(),
			turn);
	}

	public static PipelineInputs createMinimal(UserId userId, ConversationTurn turn) {
		return new PipelineInputs(userId, null, null, null, turn);
	}
}
