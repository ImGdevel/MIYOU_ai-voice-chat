package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsErrorClassifierTest {

	@Test
	@DisplayName("400 Bad Request → CLIENT_ERROR")
	void classify_400_AsClientError() {
		Exception error = WebClientResponseException.create(400, "Bad Request", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR);
	}

	@Test
	@DisplayName("401 Unauthorized → PERMANENT")
	void classify_401_AsPermanent() {
		Exception error = WebClientResponseException.create(401, "Unauthorized", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT);
	}

	@Test
	@DisplayName("402 Not Enough Credits → PERMANENT")
	void classify_402_AsPermanent() {
		Exception error = WebClientResponseException
			.create(402, "Not Enough Credits", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT);
	}

	@Test
	@DisplayName("403 Forbidden → PERMANENT")
	void classify_403_AsPermanent() {
		Exception error = WebClientResponseException.create(403, "Forbidden", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT);
	}

	@Test
	@DisplayName("404 Not Found → CLIENT_ERROR")
	void classify_404_AsClientError() {
		Exception error = WebClientResponseException.create(404, "Not Found", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR);
	}

	@Test
	@DisplayName("408 Request Timeout → TEMPORARY")
	void classify_408_AsTemporary() {
		Exception error = WebClientResponseException
			.create(408, "Request Timeout", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("429 Too Many Requests → TEMPORARY")
	void classify_429_AsTemporary() {
		Exception error = WebClientResponseException
			.create(429, "Too Many Requests", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("500 Internal Server Error → TEMPORARY")
	void classify_500_AsTemporary() {
		Exception error = WebClientResponseException
			.create(500, "Internal Server Error", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("503 Service Unavailable → TEMPORARY")
	void classify_503_AsTemporary() {
		Exception error = WebClientResponseException
			.create(503, "Service Unavailable", null, null, null);

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("TimeoutException → TEMPORARY")
	void classify_Timeout_AsTemporary() {
		Exception error = new TimeoutException("Request timeout");

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("Unknown error → TEMPORARY")
	void classify_UnknownError_AsTemporary() {
		Exception error = new RuntimeException("Unknown error");

		TtsEndpoint.FailureType result = TtsErrorClassifier.classifyError(error);

		assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY);
	}

	@Test
	@DisplayName("에러 설명 반환 - 400")
	void getErrorDescription_400() {
		String description = TtsErrorClassifier.getErrorDescription(400);

		assertThat(description).isEqualTo("잘못된 요청");
	}

	@Test
	@DisplayName("에러 설명 반환 - 402")
	void getErrorDescription_402() {
		String description = TtsErrorClassifier.getErrorDescription(402);

		assertThat(description).isEqualTo("크레딧 부족");
	}

	@Test
	@DisplayName("에러 설명 반환 - 429")
	void getErrorDescription_429() {
		String description = TtsErrorClassifier.getErrorDescription(429);

		assertThat(description).isEqualTo("요청 제한 초과");
	}
}
