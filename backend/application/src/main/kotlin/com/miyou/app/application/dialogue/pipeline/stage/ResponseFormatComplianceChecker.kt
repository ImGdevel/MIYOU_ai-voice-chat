package com.miyou.app.application.dialogue.pipeline.stage

/**
 * 대화 응답에 마크다운/이모지 잔존 여부를 검사한다. 재시도는 안 하고(스트리밍 레이턴시 비용) 위반 여부만 알려줘
 * 호출부가 메트릭 기록 등으로 쓰게 한다.
 */
object ResponseFormatComplianceChecker {
    private val MARKDOWN_PATTERN = Regex("(?m)^\\s*(#{1,6}\\s|[-*+]\\s|\\d+\\.\\s)|```")

    // 이모지는 대부분 서로게이트 쌍(U+1F300~)이라 정규식 문자클래스로 다루면 이스케이프 처리가
    // 까다롭고 오탐이 잦다 - 코드포인트 단위로 순회하며 실제 이모지 대역인지 직접 판정한다.
    private val EMOJI_CODEPOINT_RANGES =
        listOf(
            0x203C..0x2049, // 주요 특수 기호 (‼, ⁉ 등)
            0x2300..0x23FF, // 시계, 모래시계 등
            0x2600..0x27BF, // 기타 기호·딩뱃
            0x2B00..0x2BFF, // 별표(⭐), 화살표 등
            0x1F1E6..0x1F1FF, // 국가 플래그 (Regional Indicator Symbols)
            0x1F300..0x1FAFF, // 이모지 주요 대역(이모티콘, 픽토그램, 기호 등)
        )

    fun hasFormatViolation(response: String): Boolean =
        MARKDOWN_PATTERN.containsMatchIn(response) || containsEmoji(response)

    private fun containsEmoji(text: String): Boolean =
        text.codePoints().anyMatch { codePoint -> EMOJI_CODEPOINT_RANGES.any { codePoint in it } }
}
