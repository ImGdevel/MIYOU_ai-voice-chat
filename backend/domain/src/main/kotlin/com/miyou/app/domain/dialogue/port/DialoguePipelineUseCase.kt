package com.miyou.app.domain.dialogue.port

import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.ConversationSession
import reactor.core.publisher.Flux

/**
 * 대화 처리 파이프라인의 실행을 담당하는 유스케이스 인터페이스.
 */
interface DialoguePipelineUseCase {
    /**
     * 사용자의 텍스트 입력을 받아 RAG 및 LLM 처리를 수행하고, 생성된 답변을 오디오 스트림(음성)으로 반환합니다.
     *
     * @param session 현재 대화 세션 정보
     * @param text 사용자의 입력 텍스트
     * @param format 출력할 음성 오디오 포맷 (기본값: WAV)
     * @return 바이너리 오디오 데이터 스트림 (Flux)
     */
    fun executeAudioStreaming(
        session: ConversationSession,
        text: String,
        format: AudioFormat = AudioFormat.WAV,
    ): Flux<ByteArray>

    /**
     * 사용자의 텍스트 입력을 받아 RAG 및 LLM 처리를 수행하고, 생성된 답변을 텍스트 스트림으로 반환합니다.
     *
     * @param session 현재 대화 세션 정보
     * @param text 사용자의 입력 텍스트
     * @return 텍스트 토큰 스트림 (Flux)
     */
    fun executeTextOnly(
        session: ConversationSession,
        text: String,
    ): Flux<String>
}
