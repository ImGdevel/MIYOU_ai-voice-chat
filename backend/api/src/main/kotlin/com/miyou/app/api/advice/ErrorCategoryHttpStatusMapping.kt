package com.miyou.app.api.advice

import com.miyou.app.exception.ErrorCategory
import org.springframework.http.HttpStatus

/**
 * ErrorCategory -> HttpStatus 매핑. when이 exhaustive해 매핑 누락은 컴파일 에러로 드러남.
 */
object ErrorCategoryHttpStatusMapping {
    fun resolve(category: ErrorCategory): HttpStatus =
        when (category) {
            ErrorCategory.INVALID_INPUT -> HttpStatus.BAD_REQUEST
            ErrorCategory.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ErrorCategory.PAYMENT_REQUIRED -> HttpStatus.PAYMENT_REQUIRED
            ErrorCategory.NOT_FOUND -> HttpStatus.NOT_FOUND
            ErrorCategory.CONFLICT -> HttpStatus.CONFLICT
            ErrorCategory.PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE
            ErrorCategory.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
        }
}
