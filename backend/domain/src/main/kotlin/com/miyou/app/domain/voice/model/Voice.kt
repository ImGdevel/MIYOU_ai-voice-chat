package com.miyou.app.domain.voice.model

import com.miyou.app.common.model.AudioFormat

data class Voice(
    val id: String,
    val name: String,
    val provider: String,
    val settings: VoiceSettings = VoiceSettings.defaultSettings(),
    val language: String = "ko",
    val style: VoiceStyle = VoiceStyle.NEUTRAL,
    val outputFormat: AudioFormat = AudioFormat.WAV,
) {
    init {
        require(id.isNotBlank()) { "Voice id is required" }
        require(name.isNotBlank()) { "Voice name is required" }
        require(provider.isNotBlank()) { "Voice provider is required" }
    }
}
