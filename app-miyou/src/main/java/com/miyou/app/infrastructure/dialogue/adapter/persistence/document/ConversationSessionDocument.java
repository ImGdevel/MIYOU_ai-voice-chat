package com.miyou.app.infrastructure.dialogue.adapter.persistence.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversation_sessions")
@CompoundIndexes({
	@CompoundIndex(
		name = "active_user_created_idx",
		def = "{'userId': 1, 'createdAt': -1}",
		partialFilter = "{'deletedAt': {'$eq': null}}"),
	@CompoundIndex(
		name = "active_persona_created_idx",
		def = "{'personaId': 1, 'createdAt': -1}",
		partialFilter = "{'deletedAt': {'$eq': null}}"),
	@CompoundIndex(
		name = "active_persona_user_created_idx",
		def = "{'personaId': 1, 'userId': 1, 'createdAt': -1}",
		partialFilter = "{'deletedAt': {'$eq': null}}")
})
public record ConversationSessionDocument(
	@Id String sessionId,
	String personaId,
	String userId,
	Instant createdAt,
	Instant deletedAt
) {
}
