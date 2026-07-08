package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

class UnsupportedAudioFormatException(
    val format: String,
    override val cause: Throwable? = null,
) : BusinessException(
        errorCode = CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT,
        message = "${CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.message} (format: $format)",
        details = mapOf("format" to format),
        cause = cause
    )
