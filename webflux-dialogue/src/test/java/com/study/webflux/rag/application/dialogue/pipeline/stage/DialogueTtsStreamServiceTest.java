package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.dialogue.service.SentenceAssembler;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.domain.voice.port.VoiceSelectionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogueTtsStreamServiceTest {

	@Mock
	private TtsPort ttsPort;

	@Mock
	private SentenceAssembler sentenceAssembler;

	@Mock
	private PipelineTracer pipelineTracer;

	@Mock
	private VoiceSelectionPort voiceProvider;

	@InjectMocks
	private DialogueTtsStreamService service;

	@Test
	@DisplayName("TTS 웜업은 요청당 한 번만 실행된다")
	void prepareTtsWarmup_shouldRunPrepareOnlyOncePerRequest() {
		AtomicInteger prepareCallCount = new AtomicInteger();
		when(ttsPort.prepare()).thenAnswer(invocation -> Mono.defer(() -> {
			prepareCallCount.incrementAndGet();
			return Mono.empty();
		}));

		when(pipelineTracer.traceTtsPreparation(any())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Supplier<Mono<Void>> supplier = invocation.getArgument(0);
			return supplier.get();
		});

		Mono<Void> warmup = service.prepareTtsWarmup();

		StepVerifier.create(warmup.then(warmup)).verifyComplete();

		assertThat(prepareCallCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("buildAudioStream은 여러 문장에 대해 병렬로 TTS를 요청하고 순서를 보장한다")
	void buildAudioStream_shouldProcessSentencesInParallelAndPreserveOrder() {
		AtomicInteger concurrentRequests = new AtomicInteger();
		AtomicInteger maxConcurrency = new AtomicInteger();

		when(ttsPort.streamSynthesize(eq("문장1."), any(AudioFormat.class)))
			.thenAnswer(inv -> Flux.defer(() -> {
				int current = concurrentRequests.incrementAndGet();
				maxConcurrency.updateAndGet(max -> Math.max(max, current));
				return Flux.just(new byte[]{1})
					.delayElements(Duration.ofMillis(50))
					.doFinally(sig -> concurrentRequests.decrementAndGet());
			}));

		when(ttsPort.streamSynthesize(eq("문장2."), any(AudioFormat.class)))
			.thenAnswer(inv -> Flux.defer(() -> {
				int current = concurrentRequests.incrementAndGet();
				maxConcurrency.updateAndGet(max -> Math.max(max, current));
				return Flux.just(new byte[]{2})
					.delayElements(Duration.ofMillis(10))
					.doFinally(sig -> concurrentRequests.decrementAndGet());
			}));

		when(ttsPort.streamSynthesize(eq("문장3."), any(AudioFormat.class)))
			.thenAnswer(inv -> Flux.defer(() -> {
				int current = concurrentRequests.incrementAndGet();
				maxConcurrency.updateAndGet(max -> Math.max(max, current));
				return Flux.just(new byte[]{3})
					.delayElements(Duration.ofMillis(10))
					.doFinally(sig -> concurrentRequests.decrementAndGet());
			}));

		Flux<String> sentences = Flux.just("문장1.", "문장2.", "문장3.");
		Mono<Void> warmup = Mono.empty();

		Flux<byte[]> result = service.buildAudioStream(sentences, warmup, AudioFormat.WAV);

		StepVerifier.create(result)
			.expectNextMatches(b -> b[0] == 1)
			.expectNextMatches(b -> b[0] == 2)
			.expectNextMatches(b -> b[0] == 3)
			.verifyComplete();

		assertThat(maxConcurrency.get()).isGreaterThan(1);
	}
}
