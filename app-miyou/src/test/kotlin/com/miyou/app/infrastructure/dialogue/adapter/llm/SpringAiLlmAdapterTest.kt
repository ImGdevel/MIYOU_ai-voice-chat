package com.miyou.app.infrastructure.dialogue.adapter.llm

import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.model.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class SpringAiLlmAdapterTest {

	@Mock
	private lateinit var chatModel: ChatModel

	private lateinit var adapter: SpringAiLlmAdapter

	@BeforeEach
	fun setUp() {
		adapter = SpringAiLlmAdapter(chatModel)
	}

	@Test
	@DisplayName("스트리밍 완료 요청을 처리한다")
	fun streamCompletion_success() {
		val request = CompletionRequest.withMessages(listOf(Message.user("안녕하세요")), "gpt-4o-mini", true)
		`when`(chatModel.stream(any(Prompt::class.java)))
			.thenReturn(Flux.just(createChatResponse("안녕"), createChatResponse("하세요")))

		StepVerifier.create(adapter.streamCompletion(request))
			.expectNext("안녕")
			.expectNext("하세요")
			.verifyComplete()
	}

	@Test
	@DisplayName("비스트리밍 완료 요청을 처리한다")
	fun complete_success() {
		val request = CompletionRequest.withMessages(listOf(Message.user("질문")), "gpt-4o-mini", false)
		`when`(chatModel.call(any(Prompt::class.java))).thenReturn(createChatResponse("응답입니다"))

		StepVerifier.create(adapter.complete(request))
			.expectNext("응답입니다")
			.verifyComplete()
	}

	@Test
	@DisplayName("시스템과 유저 메시지를 올바르게 변환한다")
	fun convertMessages_systemMessage() {
		val request = CompletionRequest.withMessages(
			listOf(Message.system("시스템 프롬프트"), Message.user("사용자 질문")),
			"gpt-4o-mini",
			true,
		)
		val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
		`when`(chatModel.stream(any(Prompt::class.java))).thenReturn(Flux.just(createChatResponse("응답")))

		adapter.streamCompletion(request).blockLast()

		verify(chatModel).stream(promptCaptor.capture())
		val prompt = promptCaptor.value
		assertThat(prompt.instructions).hasSize(2)
		assertThat(prompt.instructions[0]).isInstanceOf(SystemMessage::class.java)
		assertThat(prompt.instructions[1]).isInstanceOf(UserMessage::class.java)
	}

	@Test
	@DisplayName("assistant 메시지를 Spring AI 타입으로 변환한다")
	fun convertMessages_assistantMessage() {
		val request = CompletionRequest.withMessages(
			listOf(Message.user("질문"), Message.assistant("이전 응답"), Message.user("추가 질문")),
			"gpt-4o-mini",
			true,
		)
		val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
		`when`(chatModel.stream(any(Prompt::class.java))).thenReturn(Flux.just(createChatResponse("새 응답")))

		adapter.streamCompletion(request).blockLast()

		verify(chatModel).stream(promptCaptor.capture())
		assertThat(promptCaptor.value.instructions[1]).isInstanceOf(AssistantMessage::class.java)
	}

	@Test
	@DisplayName("빈 청크는 그대로 완료 처리된다")
	fun streamCompletion_emptyResponse() {
		val request = CompletionRequest.withMessages(listOf(Message.user("테스트")), "gpt-4o-mini", true)
		`when`(chatModel.stream(any(Prompt::class.java))).thenReturn(Flux.just(createChatResponse("")))

		StepVerifier.create(adapter.streamCompletion(request))
			.verifyComplete()
	}

	@Test
	@DisplayName("오류를 그대로 전파한다")
	fun complete_error_propagates() {
		val request = CompletionRequest.withMessages(listOf(Message.user("테스트")), "gpt-4o-mini", false)
		`when`(chatModel.call(any(Prompt::class.java))).thenThrow(RuntimeException("Model unavailable"))

		StepVerifier.create(adapter.complete(request))
			.expectErrorMatches { throwable -> throwable.message!!.contains("Model unavailable") }
			.verify()
	}

	private fun createChatResponse(content: String): ChatResponse {
		val assistantMessage = AssistantMessage(content)
		val generation = Generation(assistantMessage)
		return ChatResponse(listOf(generation))
	}
}
