package com.miyou.app.domain.memory.model

/**
 * 기억의 정서적 현저성. importance("얼마나 중요한 사실인가")와는 독립된 축이다 -
 * SHOCKING은 importance가 낮아도 감쇠/아카이브 대상에서 제외된다(트라우마급 사건은
 * 자주 언급되지 않아도 잊으면 안 된다는 설계 의도를 실제로 구현하기 위함).
 */
enum class MemoryEmotion {
    NEUTRAL,
    POSITIVE,
    NEGATIVE,
    SHOCKING,
}
