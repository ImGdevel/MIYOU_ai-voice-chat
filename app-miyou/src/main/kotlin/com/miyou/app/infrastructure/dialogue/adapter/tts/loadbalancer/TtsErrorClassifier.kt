package com.miyou.app.infrastructure.dialogue.adapter.tts.loadbalancer

import org.springframework.web.reactive.function.client.WebClientResponseException

object TtsErrorClassifier {
    fun classifyError(error: Throwable): TtsEndpoint.FailureType {
        if (error is WebClientResponseException) {
            return classifyHttpError(error.statusCode.value())
        }

        val message = error.message.orEmpty()
        if (message.contains("timeout", ignoreCase = true) ||
            message.contains("TimeoutException", ignoreCase = true)
        ) {
            return TtsEndpoint.FailureType.TEMPORARY
        }

        return TtsEndpoint.FailureType.TEMPORARY
    }

    private fun classifyHttpError(statusCode: Int): TtsEndpoint.FailureType =
        when (statusCode) {
            400 -> TtsEndpoint.FailureType.CLIENT_ERROR
            401, 402, 403 -> TtsEndpoint.FailureType.PERMANENT
            404 -> TtsEndpoint.FailureType.CLIENT_ERROR
            408, 429, 500 -> TtsEndpoint.FailureType.TEMPORARY
            else -> if (statusCode >= 500) TtsEndpoint.FailureType.TEMPORARY else TtsEndpoint.FailureType.PERMANENT
        }

    fun getErrorDescription(statusCode: Int): String =
        when (statusCode) {
            400 -> "잘못된 요청"
            401 -> "인증 실패"
            402 -> "크레딧 부족"
            403 -> "권한 없음"
            404 -> "리소스 없음"
            408 -> "요청 시간 초과"
            429 -> "과도한 요청"
            500 -> "내부 서버 에러"
            else -> "알 수 없는 에러"
        }
}
