package com.study.webflux.rag.domain.dialogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
@CompoundIndex(def = "{'sessionId': 1, 'createdAt': -1}")
public record ConversationEntity(
	@Id String id,
	String sessionId,
	String query,
	String response,
	Instant createdAt
) {
}
