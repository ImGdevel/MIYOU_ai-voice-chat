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
import java.time.LocalDateTime

/**
 * 글로벌 예외 처리.
 *
 * 모든 예외를 표준화된 ErrorResponse로 변환.
 *
 * ## BusinessException 단일 핸들러 통합
 * 모든 비즈니스 예외는 [handleBusinessException]에서 `errorCode.category` 기반으로 일괄 처리합니다.
 * (새 예외가 추가되어도 이 파일은 수정할 필요가 없음)
 *
 * ## require()/check() 예외 처리 원칙
 * 도메인 모델의 require()/check() 실패는 검증 누락(버그)으로 간주해 500 에러로 응답합니다.
 * (상세 설계: docs/plan/2026-07-08_1432_domain-validation-error-boundary.md)
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    /**
     * BusinessException(및 모든 도메인 하위 예외) 통합 처리.
     */
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
                timestamp = LocalDateTime.now(),
                path = request.path.value(),
                details = ex.details.takeIf { it.isNotEmpty() },
            )
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * ResponseStatusException 처리.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn { "Response status exception - status=${ex.statusCode.value()}, reason=${ex.reason}" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.INVALID_REQUEST.code,
                message = ex.reason ?: "요청 처리 중 오류가 발생했습니다.",
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * 일반 Exception 처리.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unexpected exception" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.INTERNAL_SERVER_ERROR.code,
                message = CommonErrorCode.INTERNAL_SERVER_ERROR.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }
}
