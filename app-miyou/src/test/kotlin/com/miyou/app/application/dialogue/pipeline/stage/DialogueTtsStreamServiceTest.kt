package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.port.TtsPort
import com.miyou.app.domain.dialogue.service.SentenceAssembler
import com.miyou.app.domain.voice.port.VoiceSelectionPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@ExtendWith(MockitoExtension::class)
class DialogueTtsStreamServiceTest {

	@Mock
	private lateinit var ttsPort: TtsPort

	@Mock
	private lateinit var sentenceAssembler: SentenceAssembler

	@Mock
	private lateinit var pipelineTracer: PipelineTracer

	@Mock
	private lateinit var voiceProvider: VoiceSelectionPort

	@Test
	@DisplayName("TTS 준비 요청은 한 번만 수행한다")
	fun prepareTtsWarmup_shouldRunPrepareOnlyOncePerRequest() {
		val service = DialogueTtsStreamService(ttsPort, sentenceAssembler, pipelineTracer, voiceProvider)
		val prepareCallCount = AtomicInteger()

		`when`(ttsPort.prepare()).thenAnswer {
			Mono.defer {
				prepareCallCount.incrementAndGet()
				Mono.empty<Void>()
			}
		}

		`when`(pipelineTracer.traceTtsPreparation(any())).thenAnswer { invocation ->
			val supplier = invocation.getArgument<Supplier<Mono<Void>>>(0)
			supplier.get()
		}

		val warmup = service.prepareTtsWarmup()

		StepVerifier.create(warmup.then(warmup))
			.verifyComplete()

		assertThat(prepareCallCount.get()).isEqualTo(1)
	}
}
