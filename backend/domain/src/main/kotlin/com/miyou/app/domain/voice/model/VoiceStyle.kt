package com.miyou.app.domain.voice.model

enum class VoiceStyle(
    val value: String,
) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    SAD("sad"),
    ANGRY("angry"),
    EXCITED("excited"),
    ;

    companion object {
        @JvmStatic
        fun fromString(style: String?): VoiceStyle =
            entries.firstOrNull { it.value.equals(style, ignoreCase = true) } ?: NEUTRAL
    }
}
