package com.miyou.app.domain.dialogue.model

data class AudioTranscriptionInput(
    val fileName: String,
    val contentType: String,
    val audioBytes: ByteArray,
    val language: String,
) {
    init {
        require(fileName.isNotBlank()) { "fileName cannot be blank" }
        require(contentType.isNotBlank()) { "contentType cannot be blank" }
        require(audioBytes.isNotEmpty()) { "audioBytes cannot be empty" }
        require(language.isNotBlank()) { "language cannot be blank" }
    }

    fun fileName(): String = fileName

    fun contentType(): String = contentType

    fun audioBytes(): ByteArray = audioBytes

    fun language(): String = language
}
