package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

class TtsErrorClassifierTest {
    @Test
    @DisplayName("400 응답은 client error로 분류한다")
    fun classify400AsClientError() {
        val result = TtsErrorClassifier.classifyError(responseException(400, "Bad Request"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR)
    }

    @Test
    @DisplayName("401 응답은 permanent failure로 분류한다")
    fun classify401AsPermanent() {
        val result = TtsErrorClassifier.classifyError(responseException(401, "Unauthorized"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
    }

    @Test
    @DisplayName("402 응답은 permanent failure로 분류한다")
    fun classify402AsPermanent() {
        val result = TtsErrorClassifier.classifyError(responseException(402, "Payment Required"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
    }

    @Test
    @DisplayName("403 응답은 permanent failure로 분류한다")
    fun classify403AsPermanent() {
        val result = TtsErrorClassifier.classifyError(responseException(403, "Forbidden"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.PERMANENT)
    }

    @Test
    @DisplayName("404 응답은 client error로 분류한다")
    fun classify404AsClientError() {
        val result = TtsErrorClassifier.classifyError(responseException(404, "Not Found"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.CLIENT_ERROR)
    }

    @Test
    @DisplayName("408 응답은 temporary failure로 분류한다")
    fun classify408AsTemporary() {
        val result = TtsErrorClassifier.classifyError(responseException(408, "Request Timeout"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("429 응답은 temporary failure로 분류한다")
    fun classify429AsTemporary() {
        val result = TtsErrorClassifier.classifyError(responseException(429, "Too Many Requests"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("500 응답은 temporary failure로 분류한다")
    fun classify500AsTemporary() {
        val result = TtsErrorClassifier.classifyError(responseException(500, "Internal Server Error"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("503 응답은 temporary failure로 분류한다")
    fun classify503AsTemporary() {
        val result = TtsErrorClassifier.classifyError(responseException(503, "Service Unavailable"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("timeout 예외는 temporary failure로 분류한다")
    fun classifyTimeoutAsTemporary() {
        val result = TtsErrorClassifier.classifyError(TimeoutException("Request timeout"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("알 수 없는 예외는 temporary failure로 분류한다")
    fun classifyUnknownErrorAsTemporary() {
        val result = TtsErrorClassifier.classifyError(RuntimeException("Unknown error"))

        assertThat(result).isEqualTo(TtsEndpoint.FailureType.TEMPORARY)
    }

    @Test
    @DisplayName("에러 설명은 상태 코드별로 다르게 반환한다")
    fun getErrorDescription() {
        assertThat(TtsErrorClassifier.getErrorDescription(400)).isNotBlank()
        assertThat(TtsErrorClassifier.getErrorDescription(402)).isNotBlank()
        assertThat(TtsErrorClassifier.getErrorDescription(429)).isNotBlank()
        assertThat(TtsErrorClassifier.getErrorDescription(400))
            .isNotEqualTo(TtsErrorClassifier.getErrorDescription(402))
    }

    private fun responseException(
        statusCode: Int,
        statusText: String,
    ): WebClientResponseException =
        WebClientResponseException.create(
            statusCode,
            statusText,
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )
}
