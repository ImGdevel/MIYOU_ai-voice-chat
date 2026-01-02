package com.study.webflux.rag.application.dialogue.service;

import java.util.List;

import com.study.webflux.rag.application.memory.service.MemoryExtractionService;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineMonitor;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.dialogue.service.SentenceAssembler;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.port.ConversationCounterPort;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalDocument;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialoguePipelineServiceTest {

	@Mock
	private LlmPort llmPort;

	@Mock
	private TtsPort ttsPort;

	@Mock
	private RetrievalPort retrievalPort;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationCounterPort conversationCounterPort;

	@Mock
	private MemoryExtractionService memoryExtractionService;

	@Mock
	private SystemPromptService systemPromptService;

	@Mock
	private RagDialogueProperties properties;

	private SentenceAssembler sentenceAssembler;

	private DialoguePipelineService service;
	private DialoguePipelineMonitor pipelineMonitor;

	@BeforeEach
	void setUp() {
		sentenceAssembler = new SentenceAssembler();
		pipelineMonitor = new DialoguePipelineMonitor(summary -> {
		});
		var openAi = new RagDialogueProperties.OpenAi();
		openAi.setModel("test-model");
		when(properties.getOpenai()).thenReturn(openAi);
		var memory = new RagDialogueProperties.Memory();
		memory.setConversationThreshold(5);
		when(properties.getMemory()).thenReturn(memory);
		var supertone = new RagDialogueProperties.Supertone();
		supertone.setOutputFormat("wav");
		when(properties.getSupertone()).thenReturn(supertone);
		when(ttsPort.prepare()).thenReturn(Mono.empty());
		when(conversationRepository.findRecent(anyInt())).thenReturn(Flux.empty());
		when(retrievalPort.retrieveMemories(anyString(), anyInt()))
			.thenReturn(Mono.just(MemoryRetrievalResult.empty()));
		when(systemPromptService.buildSystemPrompt(any(), any())).thenReturn("");
		PipelineTracer pipelineTracer = new PipelineTracer();
		service = new DialoguePipelineService(llmPort, ttsPort, retrievalPort,
			conversationRepository, sentenceAssembler, pipelineMonitor, conversationCounterPort,
			memoryExtractionService, systemPromptService, pipelineTracer, properties);
	}

	@Test
	void executeAudioStreaming_shouldReturnRawAudioBytes() {
		String testText = "Test query";
		byte[] audioBytes1 = "audio1".getBytes();
		byte[] audioBytes2 = "audio2".getBytes();

		ConversationTurn turn = ConversationTurn.create(testText);
		RetrievalDocument doc = RetrievalDocument.of("Previous context", 5);
		RetrievalContext context = RetrievalContext.of(testText, List.of(doc));

		when(conversationRepository.save(any(ConversationTurn.class))).thenReturn(Mono.just(turn));
		when(retrievalPort.retrieve(eq(testText), eq(3))).thenReturn(Mono.just(context));
		when(llmPort.streamCompletion(any(CompletionRequest.class)))
			.thenReturn(Flux.just("First", " sentence", ".", " Second", " sentence", "."));
		when(ttsPort.streamSynthesize("First sentence.", AudioFormat.WAV))
			.thenReturn(Flux.just(audioBytes1));
		when(ttsPort.streamSynthesize("Second sentence.", AudioFormat.WAV))
			.thenReturn(Flux.just(audioBytes2));
		lenient().when(conversationCounterPort.increment()).thenReturn(Mono.just(1L));
		lenient().when(memoryExtractionService.checkAndExtract()).thenReturn(Mono.empty());

		StepVerifier.create(service.executeAudioStreaming(testText)).expectNext(audioBytes1)
			.expectNext(audioBytes2).verifyComplete();

		verify(ttsPort).streamSynthesize("First sentence.", AudioFormat.WAV);
		verify(ttsPort).streamSynthesize("Second sentence.", AudioFormat.WAV);
	}

	@Test
	void executeAudioStreaming_withEmptyContext_shouldUseDefaultPrompt() {
		String testText = "Query without context";
		byte[] audioBytes = "audio".getBytes();

		ConversationTurn turn = ConversationTurn.create(testText);
		RetrievalContext emptyContext = RetrievalContext.empty(testText);

		when(conversationRepository.save(any(ConversationTurn.class))).thenReturn(Mono.just(turn));
		when(retrievalPort.retrieve(eq(testText), eq(3))).thenReturn(Mono.just(emptyContext));
		when(llmPort.streamCompletion(any(CompletionRequest.class)))
			.thenReturn(Flux.just("Response", "."));
		when(ttsPort.streamSynthesize(anyString(), any(AudioFormat.class)))
			.thenReturn(Flux.just(audioBytes));
		lenient().when(conversationCounterPort.increment()).thenReturn(Mono.just(1L));
		lenient().when(memoryExtractionService.checkAndExtract()).thenReturn(Mono.empty());

		StepVerifier.create(service.executeAudioStreaming(testText)).expectNext(audioBytes)
			.verifyComplete();

	}

	@Test
	void executeAudioStreaming_shouldHandleMultipleSentences() {
		String testText = "Multi sentence test";
		byte[] audio1 = "a1".getBytes();
		byte[] audio2 = "a2".getBytes();
		byte[] audio3 = "a3".getBytes();

		ConversationTurn turn = ConversationTurn.create(testText);
		RetrievalContext emptyContext = RetrievalContext.empty(testText);

		when(conversationRepository.save(any(ConversationTurn.class))).thenReturn(Mono.just(turn));
		when(retrievalPort.retrieve(eq(testText), eq(3))).thenReturn(Mono.just(emptyContext));
		when(llmPort.streamCompletion(any(CompletionRequest.class)))
			.thenReturn(Flux.just("첫", "번째", ".", " 두", "번째", "!", " 세", "번째", "?"));
		when(ttsPort.streamSynthesize("첫번째.", AudioFormat.WAV)).thenReturn(Flux.just(audio1));
		when(ttsPort.streamSynthesize("두번째!", AudioFormat.WAV)).thenReturn(Flux.just(audio2));
		when(ttsPort.streamSynthesize("세번째?", AudioFormat.WAV)).thenReturn(Flux.just(audio3));
		lenient().when(conversationCounterPort.increment()).thenReturn(Mono.just(1L));
		lenient().when(memoryExtractionService.checkAndExtract()).thenReturn(Mono.empty());

		StepVerifier.create(service.executeAudioStreaming(testText)).expectNext(audio1)
			.expectNext(audio2).expectNext(audio3).verifyComplete();
	}
}
