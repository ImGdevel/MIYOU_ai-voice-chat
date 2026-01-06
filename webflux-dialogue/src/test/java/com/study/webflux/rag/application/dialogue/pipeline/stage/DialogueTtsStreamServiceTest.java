package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.dialogue.service.SentenceAssembler;
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
