package com.miyou.app.domain.auth.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

/**
 * tokenId는 세션 인증에 쓰이는 민감정보라 message/details에 담지 않는다 - BusinessException의
 * message/details 기본값(errorCode.message / emptyMap())을 그대로 쓰면 클라이언트 응답과
 * GlobalExceptionHandler 로그 양쪽 다 tokenId를 노출하지 않는다. tokenId는 내부 디버깅용
 * 생성자 프로퍼티로만 남긴다.
 */
class InvalidRefreshTokenException(
    val tokenId: String,
) : BusinessException(
        errorCode = CommonErrorCode.INVALID_REFRESH_TOKEN,
    )
