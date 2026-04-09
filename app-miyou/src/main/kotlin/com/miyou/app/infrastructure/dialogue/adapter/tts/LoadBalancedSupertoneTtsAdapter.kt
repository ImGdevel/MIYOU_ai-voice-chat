package com.miyou.app.infrastructure.dialogue.adapter.tts

import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsErrorClassifier
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class LoadBalancedSupertoneTtsAdapter(
    private val webClientBuilder: WebClient.Builder,
    private val loadBalancer: TtsLoadBalancer,
    private val voice: Voice,
) : TtsPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClientCache = ConcurrentHashMap<String, WebClient>()

    override fun streamSynthesize(
        text: String,
        format: AudioFormat?,
    ): Flux<ByteArray> = streamSynthesize(text, format, voice)

    override fun streamSynthesize(
        text: String,
        format: AudioFormat?,
        voice: Voice,
    ): Flux<ByteArray> = streamSynthesizeWithRetry(text, format, voice, 0)

    private fun streamSynthesizeWithRetry(
        text: String,
        format: AudioFormat?,
        voice: Voice,
        attemptCount: Int,
    ): Flux<ByteArray> {
        if (attemptCount >= 2) {
            return Flux.error(RuntimeException("최대 TTS 재시도 횟수(2회)를 초과했습니다"))
        }

        val endpoint = loadBalancer.selectEndpoint()
        endpoint.incrementActiveRequests()
        log.debug("TTS 엔드포인트 {} 선택, 현재 요청수 {}, 시도 횟수: {}", endpoint.id, endpoint.activeRequests, attemptCount + 1)

        return synthesizeWithEndpoint(endpoint, text, format, voice)
            .doOnCancel {
                endpoint.decrementActiveRequests()
                log.debug("TTS 엔드포인트 {} 취소됨, 현재 요청수 {}", endpoint.id, endpoint.activeRequests)
            }.doOnComplete {
                endpoint.decrementActiveRequests()
                loadBalancer.reportSuccess(endpoint)
            }.onErrorResume { error ->
                endpoint.decrementActiveRequests()
                loadBalancer.reportFailure(endpoint, error)
                when (TtsErrorClassifier.classifyError(error)) {
                    TtsEndpoint.FailureType.CLIENT_ERROR -> {
                        log.error("클라이언트 에러 발생, 재시도 중단: {}", error.message)
                        Flux.error(error)
                    }

                    else -> {
                        log.warn("TTS 엔드포인트 {} 일시 장애로 재시도 ({}회차)", endpoint.id, attemptCount + 2)
                        streamSynthesizeWithRetry(text, format, voice, attemptCount + 1)
                    }
                }
            }
    }

    private fun synthesizeWithEndpoint(
        endpoint: TtsEndpoint,
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
        val webClient = getOrCreateWebClient(endpoint)
        return webClient
            .post()
            .uri("/v1/text-to-speech/{voice_id}/stream", voice.id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .accept(MediaType.parseMediaType(outputFormat.mediaType))
            .retrieve()
            .bodyToFlux(DataBuffer::class.java)
            .timeout(Duration.ofSeconds(10))
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                org.springframework.core.io.buffer.DataBufferUtils
                    .release(dataBuffer)
                bytes
            }
    }

    private fun getOrCreateWebClient(endpoint: TtsEndpoint): WebClient =
        webClientCache.computeIfAbsent(endpoint.id) {
            webClientBuilder
                .baseUrl(endpoint.baseUrl)
                .defaultHeader("x-sup-api-key", endpoint.apiKey)
                .build()
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

    override fun prepare(): Mono<Void> =
        Flux
            .fromIterable(loadBalancer.endpoints)
            .flatMap { endpoint ->
                warmupEndpoint(endpoint)
                    .doOnSuccess { log.info("TTS 엔드포인트 {} warmup 완료", endpoint.id) }
                    .doOnError { error ->
                        log.warn("TTS 엔드포인트 {} warmup 실패, 임시 장애 처리: {}", endpoint.id, error.message)
                        endpoint.health = TtsEndpoint.EndpointHealth.TEMPORARY_FAILURE
                    }.onErrorResume { Mono.empty() }
            }.then()

    private fun warmupEndpoint(endpoint: TtsEndpoint): Mono<Void> {
        val webClient = getOrCreateWebClient(endpoint)
        return webClient
            .get()
            .uri("/v1/credits")
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(2))
            .then()
    }
}
