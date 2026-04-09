package com.miyou.app.fixture

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import java.time.Instant

object ConversationTurnFixture {

	const val DEFAULT_QUERY = "안녕하세요"
	const val DEFAULT_RESPONSE = "반갑습니다"

	@JvmStatic
	fun create(): ConversationTurn = ConversationTurn.create(ConversationSessionFixture.createId(), DEFAULT_QUERY)

	@JvmStatic
	fun create(sessionId: ConversationSessionId): ConversationTurn =
		ConversationTurn.create(sessionId, DEFAULT_QUERY)

	@JvmStatic
	fun create(query: String): ConversationTurn =
		ConversationTurn.create(ConversationSessionFixture.createId(), query)

	@JvmStatic
	fun createWithResponse(): ConversationTurn =
		ConversationTurn.create(ConversationSessionFixture.createId(), DEFAULT_QUERY)
			.withResponse(DEFAULT_RESPONSE)

	@JvmStatic
	fun createWithResponse(sessionId: ConversationSessionId): ConversationTurn =
		ConversationTurn.create(sessionId, DEFAULT_QUERY).withResponse(DEFAULT_RESPONSE)

	@JvmStatic
	fun createWithId(id: String): ConversationTurn =
		ConversationTurn.withId(
			id,
			ConversationSessionFixture.createId(),
			DEFAULT_QUERY,
			DEFAULT_RESPONSE,
			Instant.now(),
		)
}
