package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException

class InvalidAudioFileException(
    val reason: String? = null,
) : BusinessException(
        errorCode = DialogueErrorCode.INVALID_AUDIO_FILE,
        message =
            if (reason != null) {
                "${DialogueErrorCode.INVALID_AUDIO_FILE.message} (reason: $reason)"
            } else {
                DialogueErrorCode.INVALID_AUDIO_FILE.message
            },
        details =
            buildMap {
                reason?.let { put("reason", it) }
            }
    )
