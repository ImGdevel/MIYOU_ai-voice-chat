package com.miyou.app.domain.dialogue.model

import java.time.Instant

data class ConversationTurn(
    val id: String?,
    val sessionId: ConversationSessionId,
    val query: String,
    val response: String?,
    val createdAt: Instant,
) {
    init {
        require(query.isNotBlank()) { "query cannot be null or blank" }
    }

    fun withResponse(response: String?): ConversationTurn = copy(response = response)

    fun id(): String? = id

    fun sessionId(): ConversationSessionId = sessionId

    fun query(): String = query

    fun response(): String? = response

    fun createdAt(): java.time.Instant = createdAt

    companion object {
        fun create(
            sessionId: ConversationSessionId,
            query: String,
        ): ConversationTurn = ConversationTurn(null, sessionId, query, null, Instant.now())

        fun withId(
            id: String?,
            sessionId: ConversationSessionId,
            query: String,
            response: String?,
            createdAt: Instant,
        ): ConversationTurn = ConversationTurn(id, sessionId, query, response, createdAt)
    }
}
