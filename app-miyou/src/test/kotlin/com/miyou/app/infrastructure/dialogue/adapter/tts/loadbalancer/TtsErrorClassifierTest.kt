package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException

class TtsErrorClassifierTest {

	@Test
	@DisplayName("400 응답은 client error로 분류한다")
	fun classify400AsClientError() {
		val error = WebClientResponseException.create(400, "Bad Request", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR)
	}

	@Test
	@DisplayName("401 응답은 permanent failure로 분류한다")
	fun classify401AsPermanent() {
		val error = WebClientResponseException.create(401, "Unauthorized", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
	}

	@Test
	@DisplayName("402 응답은 permanent failure로 분류한다")
	fun classify402AsPermanent() {
		val error = WebClientResponseException.create(402, "Payment Required", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
	}

	@Test
	@DisplayName("403 응답은 permanent failure로 분류한다")
	fun classify403AsPermanent() {
		val error = WebClientResponseException.create(403, "Forbidden", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
	}

	@Test
	@DisplayName("404 응답은 client error로 분류한다")
	fun classify404AsClientError() {
		val error = WebClientResponseException.create(404, "Not Found", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR)
	}

	@Test
	@DisplayName("408 응답은 temporary failure로 분류한다")
	fun classify408AsTemporary() {
		val error = WebClientResponseException.create(408, "Request Timeout", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("429 응답은 temporary failure로 분류한다")
	fun classify429AsTemporary() {
		val error = WebClientResponseException.create(429, "Too Many Requests", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("500 응답은 temporary failure로 분류한다")
	fun classify500AsTemporary() {
		val error = WebClientResponseException.create(500, "Internal Server Error", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("503 응답은 temporary failure로 분류한다")
	fun classify503AsTemporary() {
		val error = WebClientResponseException.create(503, "Service Unavailable", null, null, null)

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("timeout 예외는 temporary failure로 분류한다")
	fun classifyTimeoutAsTemporary() {
		val error = TimeoutException("Request timeout")

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("알 수 없는 예외는 temporary failure로 분류한다")
	fun classifyUnknownErrorAsTemporary() {
		val error = RuntimeException("Unknown error")

		val result = TtsErrorClassifier.classifyError(error)

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
	}

	@Test
	@DisplayName("에러 설명을 상태 코드별로 반환한다")
	fun getErrorDescription() {
		assertThat(TtsErrorClassifier.getErrorDescription(400)).isNotBlank()
		assertThat(TtsErrorClassifier.getErrorDescription(402)).isNotBlank()
		assertThat(TtsErrorClassifier.getErrorDescription(429)).isNotBlank()
		assertThat(TtsErrorClassifier.getErrorDescription(400))
			.isNotEqualTo(TtsErrorClassifier.getErrorDescription(402))
	}
}
