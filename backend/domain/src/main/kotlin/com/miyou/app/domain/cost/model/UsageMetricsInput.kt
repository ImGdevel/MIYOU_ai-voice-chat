package com.miyou.app.domain.cost.model

/**
 * 비용 계산에 필요한 사용량 지표만 담는 값 객체.
 *
 * `domain.monitoring`의 [com.miyou.app.monitoring.model.UsageAnalytics]에 대한
 * 의존을 제거하기 위해, 비용 계산에 실제로 필요한 필드만 평탄화하여 보관한다.
 */
data class UsageMetricsInput(
    val model: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int,
    val inputLength: Int,
    val memoryCount: Int,
    val documentCount: Int,
    val sentenceCount: Int,
)
