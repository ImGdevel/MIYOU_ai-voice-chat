package com.miyou.app.api.dialogue

import com.miyou.app.api.common.UserIdResolver
import com.miyou.app.api.dialogue.docs.DialogueApi
import com.miyou.app.api.dialogue.dto.CreateSessionRequest
import com.miyou.app.api.dialogue.dto.CreateSessionResponse
import com.miyou.app.api.dialogue.dto.RagDialogueRequest
import com.miyou.app.api.dialogue.dto.SttTranscriptionResponse
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.dialogue.exception.SessionNotFoundException
import com.miyou.app.domain.dialogue.exception.UnsupportedAudioFormatException
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
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
        val personaId = PersonaId.ofNullable(request.personaId)
        return UserIdResolver
            .resolve(principal, request.userId)
            .map { userId -> ConversationSession.create(personaId, userId) }
            .flatMap(sessionRepository::save)
            .flatMap { saved ->
                creditChargeUseCase.initializeIfAbsent(saved.userId).thenReturn(saved)
            }.map(CreateSessionResponse::from)
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
                throw UnsupportedAudioFormatException(format, ex)
            }

        response.headers.contentType = MediaType.valueOf(targetFormat.mediaType)
        val sessionId = ConversationSessionId.of(request.sessionId)

        return sessionRepository
            .findById(sessionId)
            .switchIfEmpty(Mono.error(SessionNotFoundException(request.sessionId)))
            .flatMapMany { session ->
                dialoguePipelineUseCase.executeAudioStreaming(session, request.text, targetFormat)
            }.map(bufferFactory::wrap)
    }

    @PostMapping(path = ["/text"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun ragDialogueText(
        @Valid @RequestBody request: RagDialogueRequest,
    ): Flux<String> {
        val sessionId = ConversationSessionId.of(request.sessionId)
        return sessionRepository
            .findById(sessionId)
            .switchIfEmpty(Mono.error(SessionNotFoundException(request.sessionId)))
            .flatMapMany { session ->
                dialoguePipelineUseCase.executeTextOnly(session, request.text)
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
}
