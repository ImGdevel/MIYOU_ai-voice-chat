package com.miyou.app.exception

/**
 * 에러의 HTTP 무관 의미 카테고리. 소수 고정 집합 유지 (새 ErrorCode는 기존 값 재사용 우선).
 * HTTP status 매핑은 api 모듈의 ErrorCategoryHttpStatusMapping이 전담한다.
 */
enum class ErrorCategory {
    INVALID_INPUT,
    UNAUTHORIZED,
    PAYMENT_REQUIRED,
    NOT_FOUND,
    CONFLICT,
    PAYLOAD_TOO_LARGE,
    INTERNAL,
}
