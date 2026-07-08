package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

class SessionNotFoundException(
    val sessionId: String,
) : BusinessException(
        errorCode = CommonErrorCode.SESSION_NOT_FOUND,
        message = "${CommonErrorCode.SESSION_NOT_FOUND.message} (sessionId: $sessionId)",
        details = mapOf("sessionId" to sessionId)
    )
