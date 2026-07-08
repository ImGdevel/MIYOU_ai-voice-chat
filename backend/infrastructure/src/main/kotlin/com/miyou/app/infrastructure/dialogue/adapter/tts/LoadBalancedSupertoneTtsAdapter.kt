package com.miyou.app.infrastructure.dialogue.adapter.tts

import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.TtsCommand
import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsEndpoint
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsErrorClassifier
import com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer.TtsLoadBalancer
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class LoadBalancedSupertoneTtsAdapter(
    private val webClientBuilder: WebClient.Builder,
    private val loadBalancer: TtsLoadBalancer,
    private val voice: Voice,
    private val properties: RagDialogueProperties,
    private val meterRegistry: MeterRegistry,
) : TtsPort {
    private val log = KotlinLogging.logger {}
    private val webClientCache = ConcurrentHashMap<String, WebClient>()

    override fun streamSynthesize(command: TtsCommand): Flux<ByteArray> = streamSynthesizeWithRetry(command, 0)

    private fun streamSynthesizeWithRetry(
        command: TtsCommand,
        attemptCount: Int,
    ): Flux<ByteArray> {
        val maxRetries = properties.supertone.maxRetries
        if (attemptCount >= maxRetries) {
            return Flux.error(RuntimeException("최대 TTS 재시도 횟수(${maxRetries}회)를 초과했습니다"))
        }

        val endpoint = loadBalancer.selectEndpoint()
        endpoint.incrementActiveRequests()
        log.debug { "TTS 엔드포인트 ${endpoint.id} 선택, 현재 요청수 ${endpoint.activeRequests}, 시도 횟수: ${attemptCount + 1}" }

        return synthesizeWithEndpoint(endpoint, command)
            .doOnCancel {
                endpoint.decrementActiveRequests()
                log.debug { "TTS 엔드포인트 ${endpoint.id} 취소됨, 현재 요청수 ${endpoint.activeRequests}" }
            }.doOnComplete {
                endpoint.decrementActiveRequests()
                loadBalancer.reportSuccess(endpoint)
            }.onErrorResume { error ->
                endpoint.decrementActiveRequests()
                loadBalancer.reportFailure(endpoint, error)
                when (TtsErrorClassifier.classifyError(error)) {
                    TtsEndpoint.FailureType.CLIENT_ERROR -> {
                        log.error { "클라이언트 에러 발생, 재시도 중단: ${error.message}" }
                        Flux.error(error)
                    }

                    else -> {
                        log.warn { "TTS 엔드포인트 ${endpoint.id} 일시 장애로 재시도 (${attemptCount + 2}회차)" }
                        streamSynthesizeWithRetry(command, attemptCount + 1)
                    }
                }
            }
    }

    private fun synthesizeWithEndpoint(
        endpoint: TtsEndpoint,
        command: TtsCommand,
    ): Flux<ByteArray> {
        val outputFormat = command.format.name.lowercase()
        val voiceSettings =
            mapOf(
                "pitch_shift" to command.pitchShift,
                "pitch_variance" to command.pitchVariance,
                "speed" to command.speed,
            )
        val payload =
            mapOf(
                "text" to command.text,
                "language" to command.language,
                "style" to command.style,
                "output_format" to outputFormat,
                "voice_settings" to voiceSettings,
                "include_phonemes" to false,
            )
        val webClient = getOrCreateWebClient(endpoint)
        // 문장(호출) 단위 TTS 레이턴시/응답 크기 분포 - "각 마디마다 몇 초 걸리는지"를
        // Grafana 히스토그램으로 바로 보기 위함. 도메인 포트 시그니처는 안 건드리고
        // infra 계층에서 바로 Micrometer에 남긴다(TtsCreditMonitor와 동일 패턴).
        val sample = Timer.start(meterRegistry)
        val totalBytes = AtomicLong(0)
        return webClient
            .post()
            .uri("/v1/text-to-speech/{voice_id}/stream", voice.id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .accept(MediaType.parseMediaType(command.format.mediaType))
            .retrieve()
            .bodyToFlux(DataBuffer::class.java)
            .timeout(Duration.ofSeconds(10))
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                org.springframework.core.io.buffer.DataBufferUtils
                    .release(dataBuffer)
                totalBytes.addAndGet(bytes.size.toLong())
                bytes
            }.doOnComplete { recordCallMetrics(endpoint.id, sample, totalBytes.get()) }
            .doOnError { recordCallMetrics(endpoint.id, sample, totalBytes.get()) }
    }

    private fun recordCallMetrics(
        endpointId: String,
        sample: Timer.Sample,
        bytes: Long,
    ) {
        sample.stop(
            Timer
                .builder("tts.call.duration")
                .tag("endpoint", endpointId)
                .description("문장 1건당 TTS API 호출 소요시간")
                .register(meterRegistry),
        )
        meterRegistry
            .summary("tts.call.bytes", "endpoint", endpointId)
            .record(bytes.toDouble())
    }

    private fun getOrCreateWebClient(endpoint: TtsEndpoint): WebClient =
        webClientCache.computeIfAbsent(endpoint.id) {
            webClientBuilder
                .baseUrl(endpoint.baseUrl)
                .defaultHeader("x-sup-api-key", endpoint.apiKey)
                .build()
        }

    override fun prepare(): Mono<Void> =
        Flux
            .fromIterable(loadBalancer.endpoints)
            .flatMap { endpoint ->
                warmupEndpoint(endpoint)
                    .doOnSuccess { log.info { "TTS 엔드포인트 ${endpoint.id} warmup 완료" } }
                    .doOnError { error ->
                        log.warn { "TTS 엔드포인트 ${endpoint.id} warmup 실패, 임시 장애 처리: ${error.message}" }
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
