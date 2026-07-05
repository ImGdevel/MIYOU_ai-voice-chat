package com.miyou.app.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.infrastructure.dialogue.adapter.llm.TokenAwareLlmAdapter
import com.miyou.app.infrastructure.memory.adapter.LlmMemoryExtractionAdapter
import com.miyou.app.infrastructure.memory.adapter.MemoryExtractionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi

/**
 * 메모리 추출(LLM) 시 reasoning/importance 값이 실제로 얼마나 채워지고, importance가
 * 대화 내용의 개인적/정서적 비중에 맞게 매겨지는지 확인하는 벤치마크.
 *
 * Issue #73 항목 2("추출 결과 confidence 게이팅")의 최소 대응(reasoning 로깅)이 실제로
 * 감사 가능한 근거를 만들어내는지 - 즉 reasoning이 빈 값 없이 채워지는지 - 를
 * 실제 OpenAI API 응답으로 검증한다. 큐레이터 벤치마크와 달리 "적용 전/후"를 토글할
 * 수 있는 대상이 아니다(reasoning 파싱 자체는 항상 있었고, 이번 변경은 로깅 추가일 뿐) -
 * 따라서 비교가 아니라 커버리지/보정 관측치를 리포트한다.
 *
 * 요구사항: `OPENAI_API_KEY` 환경변수. 없으면 스킵된다. CI 기본 `test` 태스크에서는
 * 제외되고 `./gradlew memoryCuratorBenchmark`로만 수동 실행된다(같은 'benchmark' 태그 공유).
 */
@Tag("benchmark")
@DisplayName("[벤치마크] 메모리 추출 reasoning 커버리지 및 importance 보정")
class MemoryExtractionConfidenceBenchmark {
    @Test
    fun measuresReasoningCoverageAndImportanceCalibration() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY 환경변수가 없어 벤치마크를 건너뜁니다" }

        val chatModel =
            OpenAiChatModel(
                OpenAiApi(apiKey),
                OpenAiChatOptions.builder().model("gpt-4o-mini").build(),
            )
        val extractionPort =
            LlmMemoryExtractionAdapter(
                TokenAwareLlmAdapter(chatModel),
                jacksonObjectMapper(),
                MemoryExtractionConfig("gpt-4o-mini", 5, 0.2f, 0.3f),
            )

        // personal/emotional(생일), 일반 선호도, 일시적 잡담 - importance가 이 순서로
        // 매겨지는지(정서적 사건 > 선호도 > 잡담)가 이 벤치마크의 관심사.
        // "주어 생략 발화"는 한국어 특성상 주어 없이 말하는 문장을 넣어 - 추출 시스템
        // 프롬프트의 "사용자"/"AI" 명시 주어 규칙(Issue #76)이 실제로 지켜지는지 확인.
        val scenarios =
            listOf(
                "생일/정서적 사건" to "내일이 내 생일이야! 친구들 불러서 집에서 파티할 거야, 진짜 기대돼",
                "일반 선호도" to "나는 매운 음식을 잘 못 먹어서 웬만하면 순한 맛으로 시켜",
                "일시적 잡담" to "어제 저녁에 비가 꽤 많이 왔었어",
                "주어 생략 발화" to "노래 부르는 거 진짜 좋아해, 맨날 부르고 다녀",
            )

        val results =
            scenarios.map { (label, query) ->
                val sessionId = ConversationSessionId.generate()
                val context =
                    MemoryExtractionContext.of(
                        sessionId,
                        listOf(ConversationTurn.create(sessionId, query)),
                        emptyList(),
                    )
                label to extractionPort.extractMemories(context).collectList().block()!!
            }

        val allExtracted = results.flatMap { it.second }

        println(
            buildString {
                appendLine()
                appendLine("=== 메모리 추출 confidence 벤치마크 ===")
                results.forEach { (label, extracted) ->
                    appendLine("[$label]")
                    if (extracted.isEmpty()) {
                        appendLine("  (추출 없음)")
                    }
                    extracted.forEach { memory: ExtractedMemory ->
                        appendLine(
                            "  type=${memory.type} importance=${memory.importance} " +
                                "content=\"${memory.content}\" reasoning=\"${memory.reasoning}\"",
                        )
                    }
                }
                appendLine("reasoning 채움 비율: ${allExtracted.count { it.reasoning.isNotBlank() }}/${allExtracted.size}")
                val subjectCompliant = allExtracted.count { it.hasExplicitSubject() }
                appendLine("주어 명시 비율(사용자/AI로 시작): $subjectCompliant/${allExtracted.size}")
                appendLine("=======================================")
            },
        )

        assertThat(allExtracted).isNotEmpty()
        assertThat(allExtracted).allMatch { it.reasoning.isNotBlank() }
        assertThat(allExtracted.count { it.hasExplicitSubject() }).isGreaterThan(0)
    }

    private fun ExtractedMemory.hasExplicitSubject(): Boolean = content.startsWith("사용자") || content.startsWith("AI")
}
