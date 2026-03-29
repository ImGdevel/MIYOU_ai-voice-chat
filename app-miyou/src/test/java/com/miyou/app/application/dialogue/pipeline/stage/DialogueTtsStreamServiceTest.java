package com.miyou.app.application.dialogue.pipeline.stage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.miyou.app.application.monitoring.service.PipelineTracer;
import com.miyou.app.domain.dialogue.port.TtsPort;
import com.miyou.app.domain.dialogue.service.SentenceAssembler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogueTtsStreamServiceTest {

	@Mock
	private TtsPort ttsPort;

	@Mock
	private SentenceAssembler sentenceAssembler;

	@Mock
	private PipelineTracer pipelineTracer;

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
}
