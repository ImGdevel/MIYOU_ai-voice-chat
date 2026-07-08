package com.miyou.app.domain.auth.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

/**
 * tokenId는 민감정보라 message/details에 안 담음 (BusinessException 기본값 사용).
 * 내부 디버깅용 프로퍼티로만 유지.
 */
class InvalidRefreshTokenException(
    val tokenId: String,
) : BusinessException(
        errorCode = CommonErrorCode.INVALID_REFRESH_TOKEN,
    )
