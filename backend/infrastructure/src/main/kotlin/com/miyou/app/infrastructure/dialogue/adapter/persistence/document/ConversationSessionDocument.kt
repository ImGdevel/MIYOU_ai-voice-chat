package com.miyou.app.infrastructure.dialogue.adapter.persistence.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "conversation_sessions")
@CompoundIndexes(
    CompoundIndex(
        name = "active_user_created_idx",
        def = "{'userId': 1, 'createdAt': -1}",
        partialFilter = "{'deletedAt': {'${'$'}eq': null}}",
    ),
    CompoundIndex(
        name = "active_persona_created_idx",
        def = "{'personaId': 1, 'createdAt': -1}",
        partialFilter = "{'deletedAt': {'${'$'}eq': null}}",
    ),
    CompoundIndex(
        name = "active_persona_user_created_idx",
        def = "{'personaId': 1, 'userId': 1, 'createdAt': -1}",
        partialFilter = "{'deletedAt': {'${'$'}eq': null}}",
    ),
)
data class ConversationSessionDocument(
    @Id val sessionId: String,
    val personaId: String,
    val userId: String,
    val createdAt: Instant,
    val deletedAt: Instant?,
)
