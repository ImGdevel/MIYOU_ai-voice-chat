package com.miyou.app.fixture

import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import java.time.Instant

object RagDialogueRequestFixture {

	const val DEFAULT_TEXT = "테스트 질문입니다"

	@JvmStatic
	fun create(): RagDialogueRequest =
		RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, DEFAULT_TEXT, Instant.now())

	@JvmStatic
	fun createWithText(text: String): RagDialogueRequest =
		RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, text, Instant.now())

	@JvmStatic
	fun createWithSessionId(sessionId: String): RagDialogueRequest =
		RagDialogueRequest(sessionId, DEFAULT_TEXT, Instant.now())

	@JvmStatic
	fun createWithBlankText(): RagDialogueRequest =
		RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, "", Instant.now())

	@JvmStatic
	fun createWithNullTimestamp(): RagDialogueRequest =
		RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, DEFAULT_TEXT, null)
}
