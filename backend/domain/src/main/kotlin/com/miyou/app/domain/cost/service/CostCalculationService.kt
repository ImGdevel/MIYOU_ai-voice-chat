package com.miyou.app.domain.cost.service

import com.miyou.app.domain.cost.model.CostInfo
import com.miyou.app.domain.cost.model.ModelPricing
import com.miyou.app.domain.cost.model.UsageMetricsInput

/**
 * 대화 세션의 토큰 사용량 및 오디오 길이를 기반으로 비용(크레딧)을 계산하는 도메인 서비스.
 */
class CostCalculationService {
    companion object {
        /**
         * 사용량 메트릭 데이터를 기반으로 LLM 및 TTS에 대한 총 크레딧 비용을 계산합니다.
         *
         * @param input 사용량 메트릭 입력 객체 (null인 경우 0 크레딧 반환)
         * @return 계산된 LLM 및 TTS 비용 정보를 담은 [CostInfo]
         */
        @JvmStatic
        fun calculateCost(input: UsageMetricsInput?): CostInfo {
            if (input == null) {
                return CostInfo.zero()
            }

            val llmCredits = calculateLlmCredits(input)
            val ttsCredits = calculateTtsCredits(input)

            return CostInfo.of(llmCredits, ttsCredits)
        }

        private fun calculateLlmCredits(input: UsageMetricsInput): Long {
            val model = input.model.orEmpty()

            val promptTokens =
                input.promptTokens.takeIf { it != null }
                    ?: estimatePromptTokens(input)
            val completionTokens = input.completionTokens ?: input.totalTokens

            return ModelPricing.calculateLlmCredits(model, promptTokens, completionTokens)
        }

        /**
         * 입력 메트릭정보가 부족할 때, 경험적 계산식을 통해 프롬프트 토큰 수를 예측합니다.
         *
         * 예측 공식: 기본 토큰(300) + 입력 글자 수의 1/3 + (기억 데이터 수 * 50) + (조회된 문서 수 * 100)
         *
         * @param input 사용량 메트릭 입력 객체
         * @return 추정된 프롬프트 토큰 수
         */
        private fun estimatePromptTokens(input: UsageMetricsInput): Int {
            val inputLength = input.inputLength
            val memoryCount = input.memoryCount
            val documentCount = input.documentCount

            val basePromptTokens = 300
            val inputTokens = inputLength / 3
            val contextTokens = (memoryCount * 50) + (documentCount * 100)

            return basePromptTokens + inputTokens + contextTokens
        }

        private fun calculateTtsCredits(input: UsageMetricsInput): Long {
            val sentenceCount = input.sentenceCount
            val estimatedAudioLength = estimateAudioLength(sentenceCount)

            return ModelPricing.calculateTtsCredits(estimatedAudioLength)
        }

        /**
         * 문장 수에 기초해 대략적인 오디오 재생 시간(밀리초)을 예측합니다.
         *
         * 평균적으로 문장당 3초(3000ms)가 소요되는 것으로 추정합니다.
         *
         * @param sentenceCount 문장 수
         * @return 추정된 오디오 길이 (밀리초 단위)
         */
        private fun estimateAudioLength(sentenceCount: Int): Long {
            val avgSentenceDuration = 3000L
            return sentenceCount.toLong() * avgSentenceDuration
        }
    }
}
