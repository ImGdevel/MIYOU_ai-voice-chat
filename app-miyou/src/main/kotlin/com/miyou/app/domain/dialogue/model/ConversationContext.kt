package com.miyou.app.domain.dialogue.model

data class ConversationContext(
    val turns: List<ConversationTurn> = emptyList(),
) {
    fun isEmpty(): Boolean = turns.isEmpty()

    fun size(): Int = turns.size

    companion object {
        fun empty(): ConversationContext = ConversationContext(emptyList())

        fun of(turns: List<ConversationTurn>): ConversationContext = ConversationContext(turns)
    }

    fun turns(): List<ConversationTurn> = turns
}
