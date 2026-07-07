package com.miyou.app.api.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.exception.CommonErrorCode
import com.miyou.app.exception.CreditErrorCode
import com.miyou.app.exception.DialogueErrorCode
import com.miyou.app.api.dialogue.docs.DialogueApi
import com.miyou.app.api.dialogue.dto.CreateSessionRequest
import com.miyou.app.api.dialogue.dto.CreateSessionResponse
import com.miyou.app.api.dialogue.dto.RagDialogueRequest
import com.miyou.app.api.dialogue.dto.SttTranscriptionResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Validated
@RestController
@RequestMapping("/rag/dialogue")
class DialogueController(
    private val dialoguePipelineUseCase: DialoguePipelineUseCase,
    private val sessionRepository: ConversationSessionRepository,
    private val dialogueSpeechService: DialogueSpeechService,
    private val creditChargeUseCase: CreditChargeUseCase,
    private val bufferFactory: DataBufferFactory = DefaultDataBufferFactory(),
) : DialogueApi {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/session")
    override fun createSession(
        @Valid @RequestBody request: CreateSessionRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<CreateSessionResponse> {
        val personaId =
            if (request.personaId.isNotBlank()) {
                PersonaId.of(
                    request.personaId
                )
            } else {
                PersonaId.defaultPersona()
            }
        val userId = principal?.userId ?: request.userId

        val session = ConversationSession.create(personaId, userId)
        return sessionRepository
            .save(session)
            .flatMap { saved ->
                creditChargeUseCase.initializeIfAbsent(saved.userId).thenReturn(saved)
            }.map { saved ->
                CreateSessionResponse(
                    saved.sessionId.value,
                    saved.userId,
                    saved.personaId.value,
                )
            }
    }

    @PostMapping(path = ["/audio"], produces = ["audio/wav", "audio/mpeg"])
    override fun ragDialogueAudio(
        @Valid @RequestBody request: RagDialogueRequest,
        @RequestParam(defaultValue = "wav") format: String,
        response: ServerHttpResponse,
    ): Flux<DataBuffer> {
        val targetFormat =
            try {
                AudioFormat.fromString(format)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(
                    CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.httpStatus,
                    CommonErrorCode.UNSUPPORTED_AUDIO_FORMAT.message,
                    ex,
                )
            }

        response.headers.contentType = MediaType.valueOf(targetFormat.mediaType)
        val sessionId = ConversationSessionId.of(request.sessionId)

        return sessionRepository
            .findById(sessionId)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        CommonErrorCode.SESSION_NOT_FOUND.httpStatus,
                        CommonErrorCode.SESSION_NOT_FOUND.message,
                    ),
                ),
            ).flatMapMany { session ->
                dialoguePipelineUseCase.executeAudioStreaming(session, request.text, targetFormat)
            }.onErrorMap(InsufficientCreditException::class.java) {
                insufficientCreditException()
            }.map(bufferFactory::wrap)
    }

    @PostMapping(path = ["/text"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun ragDialogueText(
        @Valid @RequestBody request: RagDialogueRequest,
    ): Flux<String> {
        val sessionId = ConversationSessionId.of(request.sessionId)
        return sessionRepository
            .findById(sessionId)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        CommonErrorCode.SESSION_NOT_FOUND.httpStatus,
                        CommonErrorCode.SESSION_NOT_FOUND.message,
                    ),
                ),
            ).flatMapMany { session ->
                dialoguePipelineUseCase.executeTextOnly(session, request.text)
            }.onErrorMap(InsufficientCreditException::class.java) {
                insufficientCreditException()
            }
    }

    @PostMapping(path = ["/stt"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun ragDialogueStt(
        @RequestPart("audio") audioFile: FilePart,
        @RequestParam(required = false) language: String?,
    ): Mono<SttTranscriptionResponse> {
        logger.info { "STT request - language: $language, filename: ${audioFile.filename()}" }
        return dialogueSpeechService
            .transcribe(audioFile, language)
            .map(::SttTranscriptionResponse)
    }

    private fun insufficientCreditException(): ResponseStatusException =
        ResponseStatusException(
            CreditErrorCode.INSUFFICIENT_CREDIT.httpStatus,
            CreditErrorCode.INSUFFICIENT_CREDIT.message,
        )
}
