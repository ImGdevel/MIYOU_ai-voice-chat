package com.miyou.app.infrastructure.dialogue.adapter.persistence.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "conversations")
@CompoundIndex(def = "{'sessionId': 1, 'createdAt': -1}")
data class ConversationDocument(
    @Id val id: String?,
    val sessionId: String,
    val query: String,
    val response: String?,
    val createdAt: Instant,
)
