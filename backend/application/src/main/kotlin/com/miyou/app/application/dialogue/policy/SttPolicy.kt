package com.miyou.app.application.dialogue.policy

data class SttPolicy(
    val maxFileSizeBytes: Long,
    private val rawDefaultLanguage: String,
) {
    val defaultLanguage: String = rawDefaultLanguage.trim()

    init {
        require(maxFileSizeBytes > 0) { "maxFileSizeBytes must be positive" }
        require(this.defaultLanguage.isNotBlank()) { "defaultLanguage must not be blank" }
    }
}
