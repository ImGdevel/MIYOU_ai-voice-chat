package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.model.TtsCommand
import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.dialogue.service.SentenceAssembler
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.port.VoiceSelectionPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * TTS 스트리밍 단계.
 *
 * 문장 조립 → TTS 준비 → 음성 합성 → 오디오 스트림 생성 및 모니터링.
 */
@Service
class DialogueTtsStreamService(
    private val ttsPort: TtsPort,
    private val sentenceAssembler: SentenceAssembler,
    private val pipelineTracer: PipelineTracer,
    private val voiceProvider: VoiceSelectionPort,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * TTS 준비 (웜업).
     * 첫 합성 요청 전 리소스 초기화.
     *
     * @return 준비 완료
     */
    fun prepareTtsWarmup(): Mono<Void> =
        Mono
            .deferContextual { contextView ->
                val tracker = PipelineContext.findTracker(contextView)
                val pipelineId = tracker?.pipelineId() ?: "unknown"
                pipelineTracer.traceTtsPreparation {
                    ttsPort
                        .prepare()
                        .doOnError { error ->
                            logger.warn { "Speech synthesis warmup failed for $pipelineId: ${error.message}" }
                        }.onErrorResume { Mono.empty() }
                }
            }.cache()

    /**
     * LLM 토큰을 완전한 문장으로 조립.
     *
     * @param llmTokens LLM 토큰 스트림
     * @return 완전한 문장 스트림
     */
    fun assembleSentences(llmTokens: Flux<String>): Flux<String> =
        pipelineTracer.traceSentenceAssembly(
            { sentenceAssembler.assemble(llmTokens) },
            { tracker, sentence ->
                tracker.recordLlmOutput(sentence)
                logger.debug { "Sentence: [$sentence]" }
            },
        )

    /**
     * 오디오 스트림 생성 (기본 음성).
     *
     * @param sentences 문장 스트림
     * @param ttsWarmup TTS 준비 완료 신호
     * @param targetFormat 음성 포맷 (MP3, WAV 등)
     * @return 오디오 데이터 스트림
     */
    fun buildAudioStream(
        sentences: Flux<String>,
        ttsWarmup: Mono<Void>,
        targetFormat: AudioFormat,
    ): Flux<ByteArray> =
        sentences
            .publishOn(Schedulers.boundedElastic())
            .concatMap { sentence ->
                val command = TtsCommand(text = sentence, format = targetFormat)
                ttsWarmup.thenMany(ttsPort.streamSynthesize(command))
            }

    /**
     * 오디오 스트림 생성 (페르소나별 음성).
     *
     * @param sentences 문장 스트림
     * @param ttsWarmup TTS 준비 완료 신호
     * @param targetFormat 음성 포맷
     * @param personaId 페르소나 ID (음성 선택용)
     * @return 오디오 데이터 스트림
     */
    fun buildAudioStream(
        sentences: Flux<String>,
        ttsWarmup: Mono<Void>,
        targetFormat: AudioFormat,
        personaId: PersonaId,
    ): Flux<ByteArray> {
        val voice: Voice = voiceProvider.getVoiceForPersona(personaId.value)
        return sentences
            .publishOn(Schedulers.boundedElastic())
            .concatMap { sentence ->
                val command =
                    TtsCommand(
                        text = sentence,
                        format = targetFormat,
                        voiceId = voice.id,
                        voiceProvider = voice.provider,
                        language = voice.language,
                        style = voice.style.value,
                        pitchShift = voice.settings.pitchShift,
                        pitchVariance = voice.settings.pitchVariance,
                        speed = voice.settings.speed
                    )
                ttsWarmup.thenMany(ttsPort.streamSynthesize(command))
            }
    }

    /**
     * TTS 합성 모니터링.
     * 오디오 청크 카운팅 및 응답 전송 시점 기록.
     *
     * @param audioFlux 오디오 데이터 스트림
     * @return 모니터링이 적용된 오디오 스트림
     */
    fun traceTtsSynthesis(audioFlux: Flux<ByteArray>): Flux<ByteArray> =
        pipelineTracer.traceTtsSynthesis({ audioFlux }) { tracker, chunk ->
            tracker.incrementStageCounter(
                DialoguePipelineStage.TTS_SYNTHESIS,
                "audioChunks",
                1,
            )
            tracker.markResponseEmission()
        }
}
