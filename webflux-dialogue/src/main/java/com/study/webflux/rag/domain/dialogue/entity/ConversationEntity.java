package com.study.webflux.rag.domain.dialogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
@CompoundIndex(name = "user_time_idx", def = "{'userId': 1, 'createdAt': -1}")
public record ConversationEntity(
	@Id String id,
	@Indexed String userId,
	String query,
	String response,
	Instant createdAt
) {
}
