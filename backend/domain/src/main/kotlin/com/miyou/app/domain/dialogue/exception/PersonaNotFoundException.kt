package com.miyou.app.domain.dialogue.exception

class PersonaNotFoundException(
    val personaId: String,
) : RuntimeException("Persona not found: $personaId")
