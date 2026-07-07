package com.miyou.app.api.advice

import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.exception.UnsupportedPaymentProviderException
import com.miyou.app.domain.credit.exception.UserCreditNotFoundException
import com.miyou.app.domain.dialogue.exception.AudioFileTooLargeException
import com.miyou.app.domain.dialogue.exception.AudioTooShortException
import com.miyou.app.domain.dialogue.exception.InvalidAudioFileException
import com.miyou.app.domain.dialogue.exception.PersonaNotFoundException
import com.miyou.app.domain.dialogue.exception.SessionNotFoundException
import com.miyou.app.domain.dialogue.exception.UnsupportedAudioFormatException
import com.miyou.app.domain.mission.exception.MissionAlreadyCompletedException
import com.miyou.app.domain.mission.exception.MissionNotFoundException
import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.exception.CreditErrorCode
import com.miyou.app.exception.DialogueErrorCode
import com.miyou.app.exception.ErrorResponse
import com.miyou.app.exception.MissionErrorCode
import com.miyou.app.monitoring.exception.PipelineNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
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
     * MissionNotFoundException 처리.
     */
    @ExceptionHandler(MissionNotFoundException::class)
    fun handleMissionNotFound(ex: MissionNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Mission not found - missionId=${ex.missionId}" }
        val errorResponse =
            ErrorResponse(
                code = MissionErrorCode.MISSION_NOT_FOUND.code,
                message = MissionErrorCode.MISSION_NOT_FOUND.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(MissionErrorCode.MISSION_NOT_FOUND.httpStatus)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(errorResponse)
    }

    /**
     * MissionAlreadyCompletedException 처리.
     */
    @ExceptionHandler(MissionAlreadyCompletedException::class)
    fun handleMissionAlreadyCompleted(ex: MissionAlreadyCompletedException): ResponseEntity<ErrorResponse> {
        logger.warn { "Mission already completed - missionId=${ex.missionId}" }
        val errorResponse =
            ErrorResponse(
                code = MissionErrorCode.MISSION_ALREADY_COMPLETED.code,
                message = ex.message ?: MissionErrorCode.MISSION_ALREADY_COMPLETED.message,
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(MissionErrorCode.MISSION_ALREADY_COMPLETED.httpStatus)
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
     * PipelineNotFoundException 처리.
     */
    @ExceptionHandler(PipelineNotFoundException::class)
    fun handlePipelineNotFound(ex: PipelineNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Pipeline not found - pipelineId=${ex.pipelineId}" }
        val errorResponse =
            ErrorResponse(
                code = CommonErrorCode.RESOURCE_NOT_FOUND.code,
                message = "요청한 리소스를 찾을 수 없습니다.",
                timestamp = LocalDateTime.now(),
            )
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
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
