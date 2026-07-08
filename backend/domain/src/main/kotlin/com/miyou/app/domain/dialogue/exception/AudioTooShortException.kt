package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException

class AudioTooShortException(
    val size: Int? = null,
    val minSize: Int? = null,
) : BusinessException(
        errorCode = DialogueErrorCode.AUDIO_TOO_SHORT,
        message =
            if (size != null && minSize != null) {
                "${DialogueErrorCode.AUDIO_TOO_SHORT.message} (size: $size, minSize: $minSize)"
            } else {
                DialogueErrorCode.AUDIO_TOO_SHORT.message
            },
        details =
            buildMap {
                size?.let { put("size", it) }
                minSize?.let { put("minSize", it) }
            }
    )
