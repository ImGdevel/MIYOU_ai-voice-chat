package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput
import com.miyou.app.domain.dialogue.port.SttPort
import com.miyou.app.exception.DialogueErrorCode
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

private const val MIN_STT_AUDIO_BYTES = 1024

@Service
class DialogueSpeechService(
    private val sttPort: SttPort,
    private val sttPolicy: SttPolicy,
) {
    fun transcribe(
        filePart: FilePart,
        language: String?,
    ): Mono<String> =
        toTranscriptionInput(filePart, language)
            .flatMap(sttPort::transcribe)

    private fun toTranscriptionInput(
        filePart: FilePart,
        language: String?,
    ): Mono<AudioTranscriptionInput> {
        val contentType: MediaType? = filePart.headers().contentType
        if (contentType == null || contentType.type != "audio") {
            return Mono.error(
                ResponseStatusException(
                    DialogueErrorCode.INVALID_AUDIO_FILE.httpStatus,
                    DialogueErrorCode.INVALID_AUDIO_FILE.message,
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
                DialogueErrorCode.AUDIO_TOO_SHORT.httpStatus,
                DialogueErrorCode.AUDIO_TOO_SHORT.message,
            )
        }

        val maxFileSizeBytes = sttPolicy.maxFileSizeBytes
        if (size > maxFileSizeBytes) {
            throw ResponseStatusException(
                DialogueErrorCode.AUDIO_FILE_TOO_LARGE.httpStatus,
                DialogueErrorCode.AUDIO_FILE_TOO_LARGE.message,
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
