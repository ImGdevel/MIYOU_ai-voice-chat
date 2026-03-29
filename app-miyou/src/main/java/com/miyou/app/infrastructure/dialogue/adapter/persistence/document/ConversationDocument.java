package com.miyou.app.infrastructure.dialogue.adapter.persistence.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
@CompoundIndex(def = "{'sessionId': 1, 'createdAt': -1}")
public record ConversationDocument(
	@Id String id,
	String sessionId,
	String query,
	String response,
	Instant createdAt
) {
}
