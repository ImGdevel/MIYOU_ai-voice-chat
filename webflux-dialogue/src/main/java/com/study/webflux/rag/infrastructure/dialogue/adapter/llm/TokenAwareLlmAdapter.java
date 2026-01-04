package com.study.webflux.rag.infrastructure.dialogue.adapter.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import com.study.webflux.rag.domain.dialogue.model.Message;
import com.study.webflux.rag.domain.dialogue.model.TokenUsage;
import com.study.webflux.rag.domain.dialogue.port.LlmPort;
import com.study.webflux.rag.domain.dialogue.port.TokenUsageProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Primary
@Component
public class TokenAwareLlmAdapter implements LlmPort, TokenUsageProvider {

	private final ChatModel chatModel;
	private final Map<String, AtomicReference<TokenUsage>> usageByCorrelation = new java.util.concurrent.ConcurrentHashMap<>();

	public TokenAwareLlmAdapter(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public Flux<String> streamCompletion(CompletionRequest request) {
		List<org.springframework.ai.chat.messages.Message> messages = convertMessages(
			request.messages());

		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model(request.model())
			.streamUsage(true)
			.build();

		Prompt prompt = new Prompt(messages, options);

		return chatModel.stream(prompt)
			.doOnNext(response -> {
				if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
					var usage = response.getMetadata().getUsage();
					Long promptTokens = usage.getPromptTokens();
					Long generationTokens = usage.getGenerationTokens();
					if (promptTokens != null && generationTokens != null
						&& promptTokens <= Integer.MAX_VALUE
						&& generationTokens <= Integer.MAX_VALUE) {
						updateUsage(request, promptTokens.intValue(), generationTokens.intValue());
					}
				}
			})
			.mapNotNull(response -> {
				var generation = response.getResult();
				return generation != null && generation.getOutput() != null
					? generation.getOutput().getContent()
					: null;
			});
	}

	@Override
	public Mono<String> complete(CompletionRequest request) {
		List<org.springframework.ai.chat.messages.Message> messages = convertMessages(
			request.messages());
		Prompt prompt = new Prompt(messages);

		return Mono.fromCallable(() -> {
			ChatResponse response = chatModel.call(prompt);

			if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				var usage = response.getMetadata().getUsage();
				Long promptTokens = usage.getPromptTokens();
				Long generationTokens = usage.getGenerationTokens();
				if (promptTokens != null && generationTokens != null
					&& promptTokens <= Integer.MAX_VALUE
					&& generationTokens <= Integer.MAX_VALUE) {
					updateUsage(request, promptTokens.intValue(), generationTokens.intValue());
				}
			}

			var result = response.getResult();
			if (result == null || result.getOutput() == null) {
				throw new IllegalStateException("Invalid response from LLM");
			}
			return result.getOutput().getContent();
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private void updateUsage(CompletionRequest request, int promptTokens, int completionTokens) {
		String correlationId = request.additionalParams().getOrDefault("correlationId", "")
			.toString();
		if (!correlationId.isBlank()) {
			usageByCorrelation.computeIfAbsent(correlationId,
				id -> new AtomicReference<>(TokenUsage.zero()))
				.set(TokenUsage.of(promptTokens, completionTokens));
		}
	}

	@Override
	public java.util.Optional<TokenUsage> getTokenUsage(String correlationId) {
		if (correlationId == null || correlationId.isBlank()) {
			return java.util.Optional.empty();
		}
		AtomicReference<TokenUsage> ref = usageByCorrelation.remove(correlationId);
		return ref == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(ref.get());
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
