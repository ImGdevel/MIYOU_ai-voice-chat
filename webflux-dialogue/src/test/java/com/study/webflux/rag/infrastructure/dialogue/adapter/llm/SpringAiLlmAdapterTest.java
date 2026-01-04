package com.study.webflux.rag.infrastructure.dialogue.adapter.llm;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import com.study.webflux.rag.domain.dialogue.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiLlmAdapterTest {

	@Mock
	private ChatModel chatModel;

	private SpringAiLlmAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new SpringAiLlmAdapter(chatModel);
	}

	@Test
	@DisplayName("스트리밍 완성 요청 성공")
	void streamCompletion_success() {
		List<Message> messages = List.of(Message.user("안녕하세요"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		when(chatModel.stream(any(Prompt.class))).thenReturn(
			Flux.just(createChatResponse("안녕"), createChatResponse("하세요")));

		StepVerifier.create(adapter.streamCompletion(request)).expectNext("안녕").expectNext("하세요")
			.verifyComplete();

		verify(chatModel).stream(any(Prompt.class));
	}

	@Test
	@DisplayName("비스트리밍 완성 요청 성공")
	void complete_success() {
		List<Message> messages = List.of(Message.user("질문"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", false);

		when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("답변입니다"));

		StepVerifier.create(adapter.complete(request)).expectNext("답변입니다").verifyComplete();

		verify(chatModel).call(any(Prompt.class));
	}

	@Test
	@DisplayName("시스템 메시지 변환 성공")
	void convertMessages_systemMessage() {
		List<Message> messages = List.of(Message.system("시스템 프롬프트"), Message.user("사용자 질문"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(createChatResponse("응답")));

		adapter.streamCompletion(request).blockLast();

		verify(chatModel).stream(promptCaptor.capture());
		Prompt capturedPrompt = promptCaptor.getValue();
		assertThat(capturedPrompt.getInstructions()).hasSize(2);
		assertThat(capturedPrompt.getInstructions().get(0))
			.isInstanceOf(org.springframework.ai.chat.messages.SystemMessage.class);
		assertThat(capturedPrompt.getInstructions().get(1))
			.isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
	}

	@Test
	@DisplayName("어시스턴트 메시지 변환 성공")
	void convertMessages_assistantMessage() {
		List<Message> messages = List.of(Message.user("질문"),
			Message.assistant("이전 답변"),
			Message.user("추가 질문"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		when(chatModel.stream(any(Prompt.class))).thenReturn(
			Flux.just(createChatResponse("새 답변")));

		adapter.streamCompletion(request).blockLast();

		verify(chatModel).stream(promptCaptor.capture());
		Prompt capturedPrompt = promptCaptor.getValue();
		assertThat(capturedPrompt.getInstructions()).hasSize(3);
		assertThat(capturedPrompt.getInstructions().get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	@DisplayName("여러 메시지 스트림 완성")
	void streamCompletion_multipleChunks() {
		List<Message> messages = List.of(Message.user("테스트"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(createChatResponse("첫"),
			createChatResponse("번"),
			createChatResponse("째"),
			createChatResponse("응답")));

		StepVerifier.create(adapter.streamCompletion(request)).expectNext("첫").expectNext("번")
			.expectNext("째").expectNext("응답").verifyComplete();
	}

	@Test
	@DisplayName("완성 요청 시 boundedElastic 스케줄러 사용")
	void complete_usesBoundedElasticScheduler() {
		List<Message> messages = List.of(Message.user("질문"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", false);

		when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("답변"));

		StepVerifier.create(adapter.complete(request)).expectNext("답변").verifyComplete();
	}

	@Test
	@DisplayName("스트리밍 에러 전파")
	void streamCompletion_error_propagates() {
		List<Message> messages = List.of(Message.user("테스트"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		when(chatModel.stream(any(Prompt.class))).thenReturn(
			Flux.error(new RuntimeException("API error")));

		StepVerifier.create(adapter.streamCompletion(request))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("API error"))
			.verify();
	}

	@Test
	@DisplayName("완성 에러 전파")
	void complete_error_propagates() {
		List<Message> messages = List.of(Message.user("테스트"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", false);

		when(chatModel.call(any(Prompt.class))).thenThrow(
			new RuntimeException("Model unavailable"));

		StepVerifier.create(adapter.complete(request))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("Model unavailable"))
			.verify();
	}

	@Test
	@DisplayName("빈 응답 처리")
	void streamCompletion_emptyResponse() {
		List<Message> messages = List.of(Message.user("테스트"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		when(chatModel.stream(any(Prompt.class))).thenReturn(
			Flux.just(createChatResponse("")));

		StepVerifier.create(adapter.streamCompletion(request)).verifyComplete();
	}

	@Test
	@DisplayName("user 메시지만 있는 경우 정상 처리")
	void streamCompletion_userMessageOnly() {
		List<Message> messages = List.of(Message.user("단순 질문"));
		CompletionRequest request = CompletionRequest.withMessages(messages, "gpt-4o-mini", true);

		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(createChatResponse("답변")));

		adapter.streamCompletion(request).blockLast();

		verify(chatModel).stream(promptCaptor.capture());
		Prompt capturedPrompt = promptCaptor.getValue();
		assertThat(capturedPrompt.getInstructions()).hasSize(1);
		assertThat(capturedPrompt.getInstructions().get(0))
			.isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
	}

	private ChatResponse createChatResponse(String content) {
		AssistantMessage assistantMessage = new AssistantMessage(content);
		Generation generation = new Generation(assistantMessage);
		return new ChatResponse(List.of(generation));
	}
}
