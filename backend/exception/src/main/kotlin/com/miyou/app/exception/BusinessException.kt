package com.miyou.app.exception

/**
 * 공통 비즈니스 예외 클래스.
 *
 * 모든 커스텀 예외는 이 클래스를 상속받아야 하며,
 * GlobalExceptionHandler에서 통합 처리됩니다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    val details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)
