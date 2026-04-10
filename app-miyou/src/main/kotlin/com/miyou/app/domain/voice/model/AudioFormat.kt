package com.miyou.app.domain.voice.model

enum class AudioFormat(
    val mediaType: String,
) {
    WAV("audio/wav"),
    MP3("audio/mpeg"),
    PCM("audio/pcm"),
    ;

    companion object {
        @JvmStatic
        fun fromString(format: String): AudioFormat = valueOf(format.uppercase())
    }
}
