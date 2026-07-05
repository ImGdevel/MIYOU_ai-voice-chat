package com.miyou.app.application.common.error

import org.springframework.http.HttpStatus

/**
 * API 에러 코드 정의.
 */
interface ErrorCode {
    val code: String
    val httpStatus: HttpStatus
    val message: String
}
