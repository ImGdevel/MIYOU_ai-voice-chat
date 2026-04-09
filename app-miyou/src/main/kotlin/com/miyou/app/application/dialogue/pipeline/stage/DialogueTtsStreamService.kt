package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.PersonaId
import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.dialogue.service.SentenceAssembler
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.domain.voice.model.Voice
import com.miyou.app.domain.voice.port.VoiceSelectionPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class DialogueTtsStreamService(
    private val ttsPort: TtsPort,
    private val sentenceAssembler: SentenceAssembler,
    private val pipelineTracer: PipelineTracer,
    private val voiceProvider: VoiceSelectionPort,
) {
    private val logger = LoggerFactory.getLogger(DialogueTtsStreamService::class.java)

    fun prepareTtsWarmup(): Mono<Void> =
        Mono
            .deferContextual { contextView ->
                val tracker = PipelineContext.findTracker(contextView)
                val pipelineId = tracker?.pipelineId() ?: "unknown"
                pipelineTracer.traceTtsPreparation {
                    ttsPort
                        .prepare()
                        .doOnError { error ->
                            logger.warn("Speech synthesis warmup failed for {}: {}", pipelineId, error.message)
                        }.onErrorResume { Mono.empty() }
                }
            }.cache()

    fun assembleSentences(llmTokens: Flux<String>): Flux<String> =
        pipelineTracer.traceSentenceAssembly(
            { sentenceAssembler.assemble(llmTokens) },
            { tracker, sentence ->
                tracker.recordLlmOutput(sentence)
                logger.debug("Sentence: [{}]", sentence)
            },
        )

    fun buildAudioStream(
        sentences: Flux<String>,
        ttsWarmup: Mono<Void>,
        targetFormat: AudioFormat,
    ): Flux<ByteArray> =
        sentences
            .publishOn(Schedulers.boundedElastic())
            .concatMap { sentence ->
                ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence, targetFormat))
            }

    fun buildAudioStream(
        sentences: Flux<String>,
        ttsWarmup: Mono<Void>,
        targetFormat: AudioFormat,
        personaId: PersonaId,
    ): Flux<ByteArray> {
        val voice: Voice = voiceProvider.getVoiceForPersona(personaId)
        return sentences
            .publishOn(Schedulers.boundedElastic())
            .concatMap { sentence ->
                ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence, targetFormat, voice))
            }
    }

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
