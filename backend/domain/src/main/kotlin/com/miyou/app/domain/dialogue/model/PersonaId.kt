package com.miyou.app.domain.dialogue.model

data class PersonaId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "personaId cannot be null or blank" }
        require(value.length <= 64) { "personaId too long" }
        require(value.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            "personaId must contain only alphanumeric characters, hyphens, and underscores"
        }
    }

    companion object {
        val DEFAULT: PersonaId = PersonaId("default")

        @JvmStatic
        fun of(value: String): PersonaId = PersonaId(value)

        @JvmStatic
        fun defaultPersona(): PersonaId = DEFAULT

        @JvmStatic
        fun ofNullable(value: String?): PersonaId = if (value.isNullOrBlank()) DEFAULT else PersonaId(value)
    }

    fun value(): String = value
}
