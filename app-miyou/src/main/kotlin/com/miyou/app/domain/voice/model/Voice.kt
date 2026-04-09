package com.miyou.app.domain.voice.model

data class Voice(
    val id: String,
    val name: String,
    val provider: String,
    val settings: VoiceSettings = VoiceSettings.defaultSettings(),
    val language: String = "ko",
    val style: VoiceStyle = VoiceStyle.NEUTRAL,
    val outputFormat: AudioFormat = AudioFormat.WAV,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var id: String? = null
        private var name: String? = null
        private var provider: String? = null
        private var settings: VoiceSettings = VoiceSettings.defaultSettings()
        private var language: String = "ko"
        private var style: VoiceStyle = VoiceStyle.NEUTRAL
        private var outputFormat: AudioFormat = AudioFormat.WAV

        fun id(id: String): Builder = apply { this.id = id }

        fun name(name: String): Builder = apply { this.name = name }

        fun provider(provider: String): Builder = apply { this.provider = provider }

        fun settings(settings: VoiceSettings): Builder = apply { this.settings = settings }

        fun language(language: String): Builder = apply { this.language = language }

        fun style(style: VoiceStyle): Builder = apply { this.style = style }

        fun outputFormat(outputFormat: AudioFormat): Builder = apply { this.outputFormat = outputFormat }

        fun build(): Voice {
            require(!id.isNullOrBlank()) { "Voice id is required" }
            require(!name.isNullOrBlank()) { "Voice name is required" }
            require(!provider.isNullOrBlank()) { "Voice provider is required" }
            return Voice(
                id = requireNotNull(id),
                name = requireNotNull(name),
                provider = requireNotNull(provider),
                settings = settings,
                language = language,
                style = style,
                outputFormat = outputFormat,
            )
        }
    }
}
