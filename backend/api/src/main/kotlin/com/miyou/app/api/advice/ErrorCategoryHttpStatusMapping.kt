package com.miyou.app.api.advice

import com.miyou.app.exception.ErrorCategory
import org.springframework.http.HttpStatus

/**
 * ErrorCategory -> HttpStatus 매핑.
 *
 * domain/exception 모듈은 이 파일 존재 자체를 모른다 - "비즈니스 에러 카테고리가
 * REST에서 어떤 status로 노출되냐"는 전송 계층(api 모듈)만 결정한다. ErrorCategory가
 * enum이고 아래 when이 exhaustive해야 컴파일되므로, 새 카테고리를 추가하고 여기 매핑을
 * 깜빡하면 런타임에 조용히 500으로 새는 게 아니라 컴파일 에러로 즉시 드러난다.
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
