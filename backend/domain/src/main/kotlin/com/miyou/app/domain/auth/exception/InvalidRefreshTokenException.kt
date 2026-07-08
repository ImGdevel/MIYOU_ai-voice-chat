package com.miyou.app.domain.auth.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

class InvalidRefreshTokenException(
    val tokenId: String,
) : BusinessException(
        errorCode = CommonErrorCode.INVALID_REFRESH_TOKEN,
        message = "${CommonErrorCode.INVALID_REFRESH_TOKEN.message} (tokenId=$tokenId)",
        details = mapOf("tokenId" to tokenId)
    )
