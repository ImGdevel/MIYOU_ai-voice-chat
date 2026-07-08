package com.miyou.app.api.advice

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.exception.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * 애플리케이션의 모든 예외를 표준화된 ErrorResponse로 변환하는 글로벌 예외 핸들러.
 * BusinessException은 카테고리별로 일괄 처리하며, require()/check() 실패는 500 에러로 대응합니다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    /** BusinessException 및 하위 도메인 예외를 일괄 처리합니다. */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        val status = ErrorCategoryHttpStatusMapping.resolve(ex.errorCode.category)
        val logMessage = {
            "Business exception - code=${ex.errorCode.code}, category=${ex.errorCode.category}, details=${ex.details}"
        }
        if (status.is5xxServerError) {
            // 5xx는 서버 내부 문제 - 스택트레이스 남겨야 디버깅 가능
            logger.error(ex) { logMessage() }
        } else {
            logger.warn { logMessage() }
        }
        val errorResponse =
            ErrorResponse(
                code = ex.errorCode.code,
                message = ex.message,
                timestamp = Instant.now(),
                path = request.path.value(),
                details = ex.details.takeIf { it.isNotEmpty() },
            )
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /** ResponseStatusException을 처리합니다. */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn { "Response status exception - status=${ex.statusCode.value()}, reason=${ex.reason}" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.INVALID_REQUEST.code,
                message = ex.reason ?: "요청 처리 중 오류가 발생했습니다.",
                timestamp = Instant.now(),
            )
        return ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /** 정의되지 않은 일반 Exception을 처리합니다. */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unexpected exception" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.INTERNAL_SERVER_ERROR.code,
                message = CommonErrorCode.INTERNAL_SERVER_ERROR.message,
                timestamp = Instant.now(),
            )
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }
}
