package com.miyou.app.domain.dialogue.exception

class UnsupportedAudioFormatException(
    val format: String,
    override val cause: Throwable? = null,
) : RuntimeException("Unsupported audio format: $format", cause)
