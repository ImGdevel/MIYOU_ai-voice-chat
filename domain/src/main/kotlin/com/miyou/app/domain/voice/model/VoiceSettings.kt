package com.miyou.app.domain.voice.model

data class VoiceSettings(
    val pitchShift: Int,
    val pitchVariance: Double,
    val speed: Double,
) {
    init {
        require(pitchVariance > 0) { "pitchVariance must be positive" }
        require(speed > 0) { "speed must be positive" }
    }

    companion object {
        @JvmStatic
        fun defaultSettings(): VoiceSettings = VoiceSettings(0, 1.0, 1.1)
    }

    fun pitchShift(): Int = pitchShift

    fun pitchVariance(): Double = pitchVariance

    fun speed(): Double = speed
}
