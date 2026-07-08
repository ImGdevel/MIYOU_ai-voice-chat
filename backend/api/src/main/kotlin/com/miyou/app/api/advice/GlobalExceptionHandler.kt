package com.miyou.app.api.advice

import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.credit.exception.CreditErrorCode
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.exception.UnsupportedPaymentProviderException
import com.miyou.app.domain.credit.exception.UserCreditNotFoundException
import com.miyou.app.domain.dialogue.exception.AudioFileTooLargeException
import com.miyou.app.domain.dialogue.exception.AudioTooShortException
import com.miyou.app.domain.dialogue.exception.DialogueErrorCode
import com.miyou.app.domain.dialogue.exception.InvalidAudioFileException
import com.miyou.app.domain.dialogue.exception.PersonaNotFoundException
import com.miyou.app.domain.dialogue.exception.SessionNotFoundException
import com.miyou.app.domain.dialogue.exception.UnsupportedAudioFormatException
import com.miyou.app.domain.mission.exception.MissionErrorCode
import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.exception.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
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
     * UnsupportedPaymentProviderException 처리.
     */
    @ExceptionHandler(UnsupportedPaymentProviderException::class)
    fun handleUnsupportedPaymentProvider(ex: UnsupportedPaymentProviderException): ResponseEntity<ErrorResponse> {
        logger.warn { "Unsupported payment provider - pgProvider=${ex.pgProvider}" }
        val errorResponse =
            ErrorResponse(
                code = CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.code,
                message = CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(CreditErrorCode.UNSUPPORTED_PAYMENT_PROVIDER.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * UserCreditNotFoundException 처리.
     */
    @ExceptionHandler(UserCreditNotFoundException::class)
    fun handleUserCreditNotFound(ex: UserCreditNotFoundException): ResponseEntity<ErrorResponse> {
        logger.error { "User credit record not found - userId=${ex.userId}" }
        val errorResponse =
            ErrorResponse(
                code = CreditErrorCode.USER_CREDIT_NOT_FOUND.code,
                message = CreditErrorCode.USER_CREDIT_NOT_FOUND.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(CreditErrorCode.USER_CREDIT_NOT_FOUND.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * InvalidRefreshTokenException 처리.
     */
    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(ex: InvalidRefreshTokenException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid refresh token - tokenId=${ex.tokenId}" }
        val errorResponse =
            ErrorResponse(
                code = "INVALID_REFRESH_TOKEN",
                message = "유효하지 않거나 만료된 refresh token입니다.",
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * SessionNotFoundException 처리.
     */
    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(ex: SessionNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Session not found - sessionId=${ex.sessionId}" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.SESSION_NOT_FOUND.code,
                message = CommonErrorCode.SESSION_NOT_FOUND.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(CommonErrorCode.SESSION_NOT_FOUND.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * UnsupportedAudioFormatException 처리.
     */
    @ExceptionHandler(UnsupportedAudioFormatException::class)
    fun handleUnsupportedAudioFormat(ex: UnsupportedAudioFormatException): ResponseEntity<ErrorResponse> {
        logger.warn { "Unsupported audio format - format=${ex.format}" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.code,
                message = CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * InvalidAudioFileException 처리.
     */
    @ExceptionHandler(InvalidAudioFileException::class)
    fun handleInvalidAudioFile(ex: InvalidAudioFileException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid audio file upload" }
        val errorResponse =
            ErrorResponse(
                code = DialogueErrorCode.INVALID_AUDIO_FILE.code,
                message = DialogueErrorCode.INVALID_AUDIO_FILE.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(DialogueErrorCode.INVALID_AUDIO_FILE.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * AudioTooShortException 처리.
     */
    @ExceptionHandler(AudioTooShortException::class)
    fun handleAudioTooShort(ex: AudioTooShortException): ResponseEntity<ErrorResponse> {
        logger.warn { "Audio too short" }
        val errorResponse =
            ErrorResponse(
                code = DialogueErrorCode.AUDIO_TOO_SHORT.code,
                message = DialogueErrorCode.AUDIO_TOO_SHORT.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(DialogueErrorCode.AUDIO_TOO_SHORT.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * AudioFileTooLargeException 처리.
     */
    @ExceptionHandler(AudioFileTooLargeException::class)
    fun handleAudioFileTooLarge(ex: AudioFileTooLargeException): ResponseEntity<ErrorResponse> {
        logger.warn { "Audio file too large" }
        val errorResponse =
            ErrorResponse(
                code = DialogueErrorCode.AUDIO_FILE_TOO_LARGE.code,
                message = DialogueErrorCode.AUDIO_FILE_TOO_LARGE.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(DialogueErrorCode.AUDIO_FILE_TOO_LARGE.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * PersonaNotFoundException 처리.
     */
    @ExceptionHandler(PersonaNotFoundException::class)
    fun handlePersonaNotFound(ex: PersonaNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Persona not found - personaId=${ex.personaId}" }
        val errorResponse =
            ErrorResponse(
                code = DialogueErrorCode.PERSONA_NOT_FOUND.code,
                message = DialogueErrorCode.PERSONA_NOT_FOUND.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(DialogueErrorCode.PERSONA_NOT_FOUND.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * BusinessException 처리.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: ServerHttpRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn { "Business exception occurred - code=${ex.errorCode.code}, message=${ex.message}" }
        val errorResponse =
            ErrorResponse(
                code = ex.errorCode.code,
                message = ex.message,
                timestamp = LocalDateTime.now(),
                path = request.path.value(),
                details = ex.details.takeIf { it.isNotEmpty() },
            )
        return ResponseEntity
            .status(ex.errorCode.httpStatus)
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
                code = CommonErrorCode.INVALID_REQUEST.code,
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
}
