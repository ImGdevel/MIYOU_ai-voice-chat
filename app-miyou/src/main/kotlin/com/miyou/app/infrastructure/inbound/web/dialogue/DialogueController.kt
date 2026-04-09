package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.infrastructure.inbound.web.dialogue.docs.DialogueApi
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionRequest
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionResponse
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.SttDialogueResponse
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.SttTranscriptionResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.server.reactive.ServerHttpResponse
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

private const val CONVERSATION_COST = 100L

@Validated
@RestController
@RequestMapping("/rag/dialogue")
class DialogueController(
    private val dialoguePipelineUseCase: DialoguePipelineUseCase,
    private val sessionRepository: ConversationSessionRepository,
    private val dialogueSpeechService: DialogueSpeechService,
    private val creditQueryUseCase: CreditQueryUseCase,
    private val creditChargeUseCase: CreditChargeUseCase,
    private val bufferFactory: DataBufferFactory = DefaultDataBufferFactory(),
) : DialogueApi {
    private val logger = LoggerFactory.getLogger(DialogueController::class.java)

    @PostMapping("/session")
    override fun createSession(
        @Valid @RequestBody request: CreateSessionRequest,
    ): Mono<CreateSessionResponse> {
        val personaId =
            if (request.personaId.isNotBlank()) {
                PersonaId.of(
                    request.personaId
                )
            } else {
                PersonaId.defaultPersona()
            }
        val userId = if (request.userId.isNotBlank()) UserId.of(request.userId) else UserId.generate()

        val session = ConversationSession.create(personaId, userId)
        return sessionRepository
            .save(session)
            .flatMap { saved ->
                creditChargeUseCase.initializeIfAbsent(saved.userId).thenReturn(saved)
            }.map { saved ->
                CreateSessionResponse(
                    saved.sessionId.value,
                    saved.userId.value,
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
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 오디오 포맷입니다: $format",
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
                        HttpStatus.NOT_FOUND,
                        "해당 세션을 찾을 수 없습니다: ${request.sessionId}",
                    ),
                ),
            ).flatMapMany { session ->
                creditQueryUseCase
                    .getBalance(session.userId)
                    .filter { credit -> credit.balance >= CONVERSATION_COST }
                    .switchIfEmpty(
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.PAYMENT_REQUIRED,
                                "크레딧이 부족합니다. 크레딧을 충전하세요.",
                            ),
                        ),
                    ).flatMapMany {
                        dialoguePipelineUseCase.executeAudioStreaming(session, request.text, targetFormat)
                    }
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
                        HttpStatus.NOT_FOUND,
                        "해당 세션을 찾을 수 없습니다: ${request.sessionId}",
                    ),
                ),
            ).flatMapMany { session ->
                dialoguePipelineUseCase.executeTextOnly(session, request.text)
            }
    }

    @PostMapping(path = ["/stt"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun ragDialogueStt(
        @RequestPart("audio") audioFile: FilePart,
        @RequestParam(required = false) language: String?,
    ): Mono<SttTranscriptionResponse> {
        logger.info("STT request - language: {}, filename: {}", language, audioFile.filename())
        return dialogueSpeechService
            .transcribe(audioFile, language)
            .map(::SttTranscriptionResponse)
    }

    @PostMapping(path = ["/stt/text"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun ragDialogueSttText(
        @RequestPart("audio") audioFile: FilePart,
        @RequestParam(required = false) language: String?,
        @RequestParam @jakarta.validation.constraints.NotBlank sessionId: String,
    ): Mono<SttDialogueResponse> {
        logger.info(
            "STT/Text request - sessionId: {}, language: {}, filename: {}",
            sessionId,
            language,
            audioFile.filename(),
        )
        val sid = ConversationSessionId.of(sessionId)
        return sessionRepository
            .findById(sid)
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "해당 세션을 찾을 수 없습니다: $sessionId",
                    ),
                ),
            ).flatMap { session ->
                dialogueSpeechService.transcribeAndRespond(session, audioFile, language)
            }.map { result ->
                SttDialogueResponse(result.transcription, result.response)
            }
    }
}
