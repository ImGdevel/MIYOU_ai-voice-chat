package com.study.webflux.rag.domain.dialogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversations")
public record ConversationEntity(
	@Id String id,
	String query,
	String response,
	Instant createdAt
) {
}
