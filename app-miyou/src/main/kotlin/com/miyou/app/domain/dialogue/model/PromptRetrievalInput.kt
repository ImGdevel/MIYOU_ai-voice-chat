package com.miyou.app.domain.dialogue.model

data class PromptRetrievalInput(
    val query: String,
    val retrievedTexts: List<String> = emptyList(),
)
