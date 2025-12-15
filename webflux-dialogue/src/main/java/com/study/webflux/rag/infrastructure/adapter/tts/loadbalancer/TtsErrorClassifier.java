package com.study.webflux.rag.infrastructure.adapter.tts.loadbalancer;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * TTS API 에러 분류기
 */
public class TtsErrorClassifier {

	public static TtsEndpoint.FailureType classifyError(Throwable error) {
		if (error instanceof WebClientResponseException webClientError) {
			return classifyHttpError(webClientError.getStatusCode().value());
		}

		String message = error.getMessage();
		if (message == null) {
			return TtsEndpoint.FailureType.TEMPORARY;
		}

		if (message.contains("timeout") || message.contains("TimeoutException")) {
			return TtsEndpoint.FailureType.TEMPORARY;
		}

		return TtsEndpoint.FailureType.TEMPORARY;
	}

	private static TtsEndpoint.FailureType classifyHttpError(int statusCode) {
		return switch (statusCode) {
			case 400 -> TtsEndpoint.FailureType.CLIENT_ERROR;
			case 401 -> TtsEndpoint.FailureType.PERMANENT;
			case 402 -> TtsEndpoint.FailureType.PERMANENT;
			case 403 -> TtsEndpoint.FailureType.PERMANENT;
			case 404 -> TtsEndpoint.FailureType.CLIENT_ERROR;
			case 408 -> TtsEndpoint.FailureType.TEMPORARY;
			case 429 -> TtsEndpoint.FailureType.TEMPORARY;
			case 500 -> TtsEndpoint.FailureType.TEMPORARY;
			default -> statusCode >= 500 ? TtsEndpoint.FailureType.TEMPORARY : TtsEndpoint.FailureType.PERMANENT;
		};
	}

	public static String getErrorDescription(int statusCode) {
		return switch (statusCode) {
			case 400 -> "잘못된 요청";
			case 401 -> "인증 실패";
			case 402 -> "크레딧 부족";
			case 403 -> "권한 없음";
			case 404 -> "리소스 없음";
			case 408 -> "요청 타임아웃";
			case 429 -> "요청 제한 초과";
			case 500 -> "서버 내부 오류";
			default -> "알 수 없는 오류";
		};
	}
}
