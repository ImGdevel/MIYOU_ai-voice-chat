package com.miyou.app.infrastructure.inbound.web.advice

import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.exception.CreditErrorCode
import com.miyou.app.exception.DialogueErrorCode
import com.miyou.app.exception.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * 글로벌 예외 처리.
 *
 * 모든 예외를 표준화된 ErrorResponse로 변환.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    /**
     * InsufficientCreditException 처리.
     */
    @ExceptionHandler(InsufficientCreditException::class)
    fun handleInsufficientCredit(ex: InsufficientCreditException): ResponseEntity<ErrorResponse> {
        logger.warn {
            "Insufficient credit - userId=${ex.userId}, current=${ex.currentBalance}, required=${ex.requiredAmount}"
        }
        val errorResponse =
            ErrorResponse(
                code = CreditErrorCode.INSUFFICIENT_CREDIT.code,
                message = CreditErrorCode.INSUFFICIENT_CREDIT.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(CreditErrorCode.INSUFFICIENT_CREDIT.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
                code = determineErrorCode(ex.statusCode, ex.reason),
                message = ex.reason ?: "요청 처리 중 오류가 발생했습니다.",
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(ex.statusCode)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * HTTP 상태 코드와 이유로부터 에러 코드 결정.
     */
    private fun determineErrorCode(
        httpStatus: HttpStatusCode,
        reason: String?,
    ): String =
        when {
            httpStatus == HttpStatus.NOT_FOUND -> {
                when {
                    reason?.contains("세션") == true -> "SESSION_NOT_FOUND"
                    reason?.contains("미션") == true -> "MISSION_NOT_FOUND"
                    reason?.contains("페르소나") == true -> "PERSONA_NOT_FOUND"
                    else -> CommonErrorCode.RESOURCE_NOT_FOUND.code
                }
            }

            httpStatus == HttpStatus.CONFLICT -> {
                when {
                    reason?.contains("완료") == true -> "MISSION_ALREADY_COMPLETED"
                    else -> CommonErrorCode.CONFLICT.code
                }
            }

            httpStatus == HttpStatus.BAD_REQUEST -> {
                when {
                    reason?.contains("포맷") == true -> CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.code
                    else -> CommonErrorCode.INVALID_REQUEST.code
                }
            }

            else -> {
                CommonErrorCode.INTERNAL_SERVER_ERROR.code
            }
        }
}
