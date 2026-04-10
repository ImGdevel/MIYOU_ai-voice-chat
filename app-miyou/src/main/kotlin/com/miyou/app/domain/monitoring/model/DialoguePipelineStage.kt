package com.miyou.app.domain.monitoring.model

/**
 * 대화 파이프라인의 처리 단계를 표현합니다.
 */
enum class DialoguePipelineStage {
    /** MongoDB에 사용자 요청을 저장합니다. */
    QUERY_PERSISTENCE,

    /** 벡터 DB에서 기억을 검색합니다. */
    MEMORY_RETRIEVAL,

    /** 문서 검색을 수행합니다. */
    RETRIEVAL,

    /** LLM 프롬프트를 구성합니다. */
    PROMPT_BUILDING,

    /** LLM 토큰 생성 응답을 수집합니다. */
    LLM_COMPLETION,

    /** 토큰을 문장 단위로 결합합니다. */
    SENTENCE_ASSEMBLY,

    /** TTS 합성을 위한 문장 사전 처리 단계입니다. */
    TTS_PREPARATION,

    /** Supertone TTS로 문장을 오디오로 변환합니다. */
    TTS_SYNTHESIS,
}
