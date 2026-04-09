package com.miyou.app.application.dialogue.policy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SttPolicyTest {
    @Test
    @DisplayName("생성 시 기본 언어의 앞뒤 공백을 제거한다")
    fun constructor_shouldTrimDefaultLanguage() {
        val policy = SttPolicy(1024L, " ko ")

        assertThat(policy.defaultLanguage).isEqualTo("ko")
    }

    @Test
    @DisplayName("생성 시 최대 파일 크기가 0 이하이면 예외가 발생한다")
    fun constructor_shouldRejectNonPositiveMaxFileSizeBytes() {
        assertThatThrownBy { SttPolicy(0L, "ko") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("maxFileSizeBytes must be positive")
    }

    @Test
    @DisplayName("생성 시 기본 언어가 비어 있으면 예외가 발생한다")
    fun constructor_shouldRejectBlankDefaultLanguage() {
        assertThatThrownBy { SttPolicy(1024L, "   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("defaultLanguage must not be blank")
    }
}
