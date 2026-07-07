package com.miyou.app.domain.dialogue.model

import com.miyou.app.common.model.AudioFormat

data class TtsCommand(
    val text: String,
    val format: AudioFormat,
    val voiceId: String? = null,
    val voiceProvider: String? = null,
    val language: String = "ko",
    val style: String = "neutral",
    val pitchShift: Int = 0,
    val pitchVariance: Double = 0.0,
    val speed: Double = 1.0,
)
