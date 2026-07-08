package com.miyou.app.exception

/**
 * 에러의 HTTP 무관 의미 카테고리.
 *
 * 소수 고정 집합으로 유지한다 - 새 ErrorCode를 추가할 때 대부분 기존 카테고리 중
 * 하나를 재사용해야 하며, 새 카테고리 추가는 신중히 판단한다. HTTP status로의 변환은
 * api 모듈의 ErrorCategoryHttpStatusMapping이 전담하며, 도메인/exception 모듈은
 * 이 값이 최종적으로 어떤 프로토콜(REST/gRPC 등)로 노출되는지 알지 못한다.
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
