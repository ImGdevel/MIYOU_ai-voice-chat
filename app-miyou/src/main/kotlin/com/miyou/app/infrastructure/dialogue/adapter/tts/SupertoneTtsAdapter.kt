package com.miyou.app.infrastructure.dialogue.adapter.tts

import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class SupertoneTtsAdapter(
    webClientBuilder: WebClient.Builder,
    config: SupertoneConfig,
    private val voice: Voice,
) : TtsPort {
    private val webClient: WebClient =
        webClientBuilder
            .baseUrl(config.baseUrl)
            .defaultHeader("x-sup-api-key", config.apiKey)
            .build()

    private val warmupMono: Mono<Void> =
        webClient
            .method(HttpMethod.HEAD)
            .uri("/")
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(2))
            .onErrorResume { Mono.empty() }
            .then()
            .cache()

    override fun streamSynthesize(
        text: String,
        format: AudioFormat?,
    ): Flux<ByteArray> = streamSynthesize(text, format, voice)

    override fun streamSynthesize(
        text: String,
        format: AudioFormat?,
        voice: Voice,
    ): Flux<ByteArray> {
        val outputFormat = format ?: voice.outputFormat
        val settings = voice.settings
        val voiceSettings =
            mapOf(
                "pitch_shift" to settings.pitchShift,
                "pitch_variance" to settings.pitchVariance,
                "speed" to settings.speed,
            )
        val payload =
            mapOf(
                "text" to text,
                "language" to voice.language,
                "style" to voice.style.value,
                "output_format" to outputFormat.name.lowercase(),
                "voice_settings" to voiceSettings,
                "include_phonemes" to false,
            )

        return webClient
            .post()
            .uri("/v1/text-to-speech/{voice_id}/stream", voice.id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .accept(MediaType.parseMediaType(outputFormat.mediaType))
            .retrieve()
            .bodyToFlux(DataBuffer::class.java)
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                org.springframework.core.io.buffer.DataBufferUtils
                    .release(dataBuffer)
                bytes
            }
    }

    override fun synthesize(
        text: String,
        format: AudioFormat?,
    ): Mono<ByteArray> = synthesize(text, format, voice)

    override fun synthesize(
        text: String,
        format: AudioFormat?,
        voice: Voice,
    ): Mono<ByteArray> =
        streamSynthesize(text, format, voice)
            .collectList()
            .map { byteArrays ->
                val totalSize = byteArrays.sumOf { it.size }
                val result = ByteArray(totalSize)
                var offset = 0
                for (bytes in byteArrays) {
                    bytes.copyInto(result, offset)
                    offset += bytes.size
                }
                result
            }

    override fun prepare(): Mono<Void> = warmupMono
}
