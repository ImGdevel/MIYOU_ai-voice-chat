package com.miyou.app.infrastructure.dialogue.adapter.stt

import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput
import com.miyou.app.domain.dialogue.port.SttPort
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * OpenAI Whisper API STT adapter.
 */
class OpenAiWhisperSttAdapter(
    webClientBuilder: WebClient.Builder,
    apiKey: String,
    baseUrl: String,
    private val model: String,
) : SttPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient: WebClient =
        webClientBuilder
            .baseUrl(normalizeBaseUrl(baseUrl))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .build()

    override fun transcribe(input: AudioTranscriptionInput): Mono<String> {
        log.info(
            "STT request - fileName: {}, contentType: {}, size: {} bytes",
            input.fileName(),
            input.contentType(),
            input.audioBytes().size,
        )

        val builder =
            MultipartBodyBuilder().apply {
                part("model", model)
                if (!input.language().isNullOrBlank()) {
                    part("language", input.language())
                }
                part("response_format", "json")

                val audioResource =
                    object : ByteArrayResource(input.audioBytes()) {
                        override fun getFilename(): String = input.fileName()
                    }
                part("file", audioResource)
                    .contentType(MediaType.parseMediaType(input.contentType()))
            }

        return webClient
            .post()
            .uri("/audio/transcriptions")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .onStatus({ status -> status.is4xxClientError || status.is5xxServerError }, { response ->
                response
                    .bodyToMono(String::class.java)
                    .doOnNext { body ->
                        log.error("OpenAI API error - status: {}, body: {}", response.statusCode(), body)
                    }.then(Mono.error(RuntimeException("OpenAI API error: ${response.statusCode()}")))
            })
            .bodyToMono(OpenAiTranscriptionResponse::class.java)
            .map { response -> checkNotNull(response.text) { "OpenAI STT response text is null" } }
            .doOnSuccess { text -> log.info("STT completed: {} chars", text.length) }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        var normalized = if (baseUrl.isBlank()) "https://api.openai.com" else baseUrl.trim()
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        return if (normalized.endsWith("/v1")) normalized else "$normalized/v1"
    }

    private data class OpenAiTranscriptionResponse(
        val text: String?,
    )
}
