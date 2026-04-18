package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.dialogue.port.SttPort
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

private const val MIN_STT_AUDIO_BYTES = 1024

@Service
class DialogueSpeechService(
    private val sttPort: SttPort,
    private val dialoguePipelineUseCase: DialoguePipelineUseCase,
    private val sttPolicy: SttPolicy,
) {
    fun transcribe(
        filePart: FilePart,
        language: String?,
    ): Mono<String> =
        toTranscriptionInput(filePart, language)
            .flatMap(sttPort::transcribe)

    fun transcribeAndRespond(
        session: ConversationSession,
        filePart: FilePart,
        language: String?,
    ): Mono<SpeechDialogueResult> =
        transcribe(filePart, language)
            .flatMap { transcription ->
                dialoguePipelineUseCase
                    .executeTextOnly(session, transcription)
                    .collectList()
                    .map { tokens -> SpeechDialogueResult(transcription, tokens.joinToString(separator = "")) }
            }

    data class SpeechDialogueResult(
        val transcription: String,
        val response: String,
    )

    private fun toTranscriptionInput(
        filePart: FilePart,
        language: String?,
    ): Mono<AudioTranscriptionInput> {
        val contentType: MediaType? = filePart.headers().contentType
        if (contentType == null || contentType.type != "audio") {
            return Mono.error(
                ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "오디오 파일만 업로드해 주세요",
                ),
            )
        }

        return DataBufferUtils
            .join(filePart.content())
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                try {
                    validateAudioSize(bytes.size)
                    val targetLanguage = normalizeLanguage(language)
                    AudioTranscriptionInput(filePart.filename(), contentType.toString(), bytes, targetLanguage)
                } finally {
                    DataBufferUtils.release(dataBuffer)
                }
            }
    }

    private fun validateAudioSize(size: Int) {
        if (size < MIN_STT_AUDIO_BYTES) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "음성이 너무 짧습니다. 조금 더 길게 말한 뒤 전송해 주세요.",
            )
        }

        val maxFileSizeBytes = sttPolicy.maxFileSizeBytes
        if (size > maxFileSizeBytes) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "음성 파일 크기가 너무 큽니다. 최대값: $maxFileSizeBytes bytes",
            )
        }
    }

    private fun normalizeLanguage(language: String?): String {
        if (!language.isNullOrBlank()) {
            return language
        }
        val defaultLanguage = sttPolicy.defaultLanguage
        return defaultLanguage.ifBlank { "en" }
    }
}
