package com.miyou.app.application.dialogue.service

import com.miyou.app.application.dialogue.policy.SttPolicy
import com.miyou.app.domain.dialogue.exception.AudioFileTooLargeException
import com.miyou.app.domain.dialogue.exception.AudioTooShortException
import com.miyou.app.domain.dialogue.exception.InvalidAudioFileException
import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput
import com.miyou.app.domain.dialogue.port.SttPort
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
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
            return Mono.error(InvalidAudioFileException())
        }
        // AudioTranscriptionInput의 require(fileName.isNotBlank())까지 새어들어가면 400이
        // 아니라 500으로 응답된다 - multipart 파일명은 Bean Validation 대상이 아니라서
        // 여기서 직접 막아야 한다.
        if (filePart.filename().isBlank()) {
            return Mono.error(InvalidAudioFileException())
        }

        return DataBufferUtils
            .join(filePart.content())
            .flatMap { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                DataBufferUtils.release(dataBuffer)

                validateAudioSize(bytes.size)
                    .map {
                        val targetLanguage = normalizeLanguage(language)
                        AudioTranscriptionInput(filePart.filename(), contentType.toString(), bytes, targetLanguage)
                    }
            }
    }

    private fun validateAudioSize(size: Int): Mono<Unit> {
        if (size < MIN_STT_AUDIO_BYTES) {
            return Mono.error(AudioTooShortException())
        }

        val maxFileSizeBytes = sttPolicy.maxFileSizeBytes
        if (size > maxFileSizeBytes) {
            return Mono.error(AudioFileTooLargeException())
        }
        return Mono.just(Unit)
    }

    private fun normalizeLanguage(language: String?): String {
        if (!language.isNullOrBlank()) {
            return language
        }
        val defaultLanguage = sttPolicy.defaultLanguage
        return defaultLanguage.ifBlank { "en" }
    }
}
