package com.miyou.app.exception

import org.springframework.http.HttpStatus

/**
 * 크레딧 관련 에러 코드.
 */
enum class CreditErrorCode(
    override val code: String,
    override val httpStatus: HttpStatus,
    override val message: String,
) : ErrorCode {
    INSUFFICIENT_CREDIT(
        "INSUFFICIENT_CREDIT",
        HttpStatus.PAYMENT_REQUIRED,
        "크레딧이 부족합니다. 크레딧을 충전하세요.",
    ),
    INVALID_CREDIT_AMOUNT(
        "INVALID_CREDIT_AMOUNT",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 크레딧 금액입니다.",
    ),
    CREDIT_INITIALIZATION_FAILED(
        "CREDIT_INITIALIZATION_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "크레딧 초기화에 실패했습니다.",
    ),
    UNSUPPORTED_PAYMENT_PROVIDER(
        "UNSUPPORTED_PAYMENT_PROVIDER",
        HttpStatus.BAD_REQUEST,
        "지원하지 않는 결제 제공자입니다.",
    ),
    USER_CREDIT_NOT_FOUND(
        "USER_CREDIT_NOT_FOUND",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "사용자 크레딧 기록을 찾을 수 없습니다.",
    ),
}
