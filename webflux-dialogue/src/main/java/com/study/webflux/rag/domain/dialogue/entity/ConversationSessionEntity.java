package com.study.webflux.rag.domain.dialogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversation_sessions")
@CompoundIndexes({
	@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
	@CompoundIndex(name = "persona_created_idx", def = "{'personaId': 1, 'createdAt': -1}"),
	@CompoundIndex(name = "persona_user_created_idx", def = "{'personaId': 1, 'userId': 1, 'createdAt': -1}")
})
public record ConversationSessionEntity(
	@Id String sessionId,
	String personaId,
	String userId,
	Instant createdAt,
	Instant deletedAt
) {
}
