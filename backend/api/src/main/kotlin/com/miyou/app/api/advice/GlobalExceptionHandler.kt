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
 * ## BusinessException은 단일 핸들러로 통합 처리
 * 예전엔 구체 예외 타입마다 [ExceptionHandler]를 따로 뒀는데, 전부 동일한
 * `code/message/details` 조립 로직을 반복하면서 일부는 details/path를 응답에서
 * 빠뜨리는 버그가 있었다. 지금은 [handleBusinessException] 하나가 [BusinessException]의
 * `errorCode.category`를 [ErrorCategoryHttpStatusMapping]으로 HttpStatus로 변환해
 * 일괄 처리한다 - 새 도메인 예외를 추가해도 이 파일은 안 건드려도 된다.
 *
 * ## require()/check() 예외가 여기 안 걸리는 이유
 * 도메인 모델의 `require()`/`check()`가 던지는 `IllegalArgumentException`/
 * `IllegalStateException`은 의도적으로 전용 핸들러가 없다 - 맨 아래
 * [handleGenericException]에서 500으로 처리된다. 이게 기본값이어야 하는 이유:
 * client가 보낸 원시값은 도메인 생성자에 닿기 전에 DTO Bean Validation(또는
 * 명시적 검증)으로 이미 걸러졌어야 한다. 그 지점을 통과한 뒤에도 require()가
 * 터진다면 상위 계층이 보장했어야 할 계약이 깨진 것 - 즉 버그다. 블랭킷
 * `IllegalArgumentException -> 400` 매핑을 일부러 안 만든 이유도 이거다 -
 * 그러면 이런 계약 위반(버그)까지 "client 잘못"으로 위장되고, 검증 갭이
 * 조용히 400으로 삼켜져서 아무도 눈치 못 챈다.
 *
 * 새 DTO 필드가 도메인 값객체 생성자에 들어간다면, 그 값객체의 require() 조건과
 * DTO의 Bean Validation 애노테이션을 반드시 1:1로 맞춰야 한다.
 * 상세: docs/plan/2026-07-08_1432_domain-validation-error-boundary.md
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
