package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.CompletionRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * LLM(대형 언어 모델) 서비스와의 통신을 추상화한 아웃포트 인터페이스.
 */
interface LlmPort {
    /**
     * LLM의 답변을 토큰 단위로 실시간 스트리밍합니다.
     *
     * @param request LLM 호출 요청 정보 (프롬프트, 모델 설정 등)
     * @return 텍스트 토큰 스트림 (Flux)
     */
    fun streamCompletion(request: CompletionRequest): Flux<String>

    /**
     * LLM의 답변을 단건으로 모두 완성하여 반환합니다.
     *
     * @param request LLM 호출 요청 정보
     * @return 완성된 텍스트 응답 (Mono)
     */
    fun complete(request: CompletionRequest): Mono<String>
}
