package com.study.webflux.rag.infrastructure.adapter.llm;

import com.study.webflux.rag.domain.model.llm.CompletionRequest;
import com.study.webflux.rag.domain.model.llm.Message;
import com.study.webflux.rag.domain.port.out.LlmPort;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Primary
@Component
public class SpringAiLlmAdapter implements LlmPort {

	private final ChatClient chatClient;

	public SpringAiLlmAdapter(ChatModel chatModel) {
		this.chatClient = ChatClient.builder(chatModel).build();
	}

	@Override
	public Flux<String> streamCompletion(CompletionRequest request) {
		List<org.springframework.ai.chat.messages.Message> springAiMessages = convertMessages(
			request.messages());
		Prompt prompt = new Prompt(springAiMessages);

		return chatClient.prompt(prompt).stream().content();
	}

	@Override
	public Mono<String> complete(CompletionRequest request) {
		List<org.springframework.ai.chat.messages.Message> springAiMessages = convertMessages(
			request.messages());
		Prompt prompt = new Prompt(springAiMessages);

		return Mono.fromCallable(() -> chatClient.prompt(prompt).call().content())
			.subscribeOn(Schedulers.boundedElastic());
	}

	private List<org.springframework.ai.chat.messages.Message> convertMessages(
		List<Message> messages) {
		return messages.stream().map(this::convertMessage).collect(Collectors.toList());
	}

	private org.springframework.ai.chat.messages.Message convertMessage(Message message) {
		return switch (message.role()) {
			case SYSTEM -> new SystemMessage(message.content());
			case USER -> new UserMessage(message.content());
			case ASSISTANT -> new AssistantMessage(message.content());
		};
	}
}
