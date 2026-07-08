package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException

class PersonaNotFoundException(
    val personaId: String,
) : BusinessException(
        errorCode = DialogueErrorCode.PERSONA_NOT_FOUND,
        message = "${DialogueErrorCode.PERSONA_NOT_FOUND.message} (personaId: $personaId)",
        details = mapOf("personaId" to personaId)
    )
