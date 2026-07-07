package com.miyou.app.domain.cost.model

/**
 * LLM/TTS 비용 계산 결과를 보관하는 값 객체.
 */
data class CostInfo(
    val llmCredits: Long,
    val ttsCredits: Long,
    val totalCredits: Long,
) {
    companion object {
        @JvmStatic
        fun of(
            llmCredits: Long,
            ttsCredits: Long,
        ): CostInfo = CostInfo(llmCredits, ttsCredits, llmCredits + ttsCredits)

        @JvmStatic
        fun zero(): CostInfo = CostInfo(0L, 0L, 0L)
    }
}
