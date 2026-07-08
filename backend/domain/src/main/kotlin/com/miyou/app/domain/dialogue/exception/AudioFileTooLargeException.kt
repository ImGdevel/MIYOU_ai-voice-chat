package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException

class AudioFileTooLargeException(
    val size: Int? = null,
    val maxSize: Int? = null,
) : BusinessException(
        errorCode = DialogueErrorCode.AUDIO_FILE_TOO_LARGE,
        message =
            if (size != null && maxSize != null) {
                "${DialogueErrorCode.AUDIO_FILE_TOO_LARGE.message} (size: $size, maxSize: $maxSize)"
            } else {
                DialogueErrorCode.AUDIO_FILE_TOO_LARGE.message
            },
        details =
            buildMap {
                size?.let { put("size", it) }
                maxSize?.let { put("maxSize", it) }
            }
    )
