package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import reactor.core.publisher.Flux

/**
 * 대화 맥락으로부터 새로운 기억(사용자 정보, 선호도 등)을 추출하는 외부 AI 엔진 연동 포트.
 */
interface MemoryExtractionPort {
    /**
     * 주어진 대화 스니펫 및 기존 기억들을 바탕으로 새로운 기억들을 추출합니다.
     *
     * @param context 기억 추출에 필요한 대화 이력 및 기존 컨텍스트 정보
     * @return 추출된 기억 객체 스트림 (Flux)
     */
    fun extractMemories(context: MemoryExtractionContext): Flux<ExtractedMemory>
}
