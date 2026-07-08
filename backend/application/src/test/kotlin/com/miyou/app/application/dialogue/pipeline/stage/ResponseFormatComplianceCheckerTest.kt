package com.miyou.app.application.dialogue.pipeline.stage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResponseFormatComplianceCheckerTest {
    @Test
    @DisplayName("순수 대화체 텍스트는 위반이 아니다")
    fun hasFormatViolation_shouldReturnFalseForPlainDialogue() {
        val response = "주인님, 바로 정리해드릴게요. 먼저 필요한 파일을 확인하고, 곧바로 수정 방법을 말씀드릴게요."

        assertThat(ResponseFormatComplianceChecker.hasFormatViolation(response)).isFalse()
    }

    @Test
    @DisplayName("마크다운 불릿이 섞이면 위반이다")
    fun hasFormatViolation_shouldReturnTrueForBulletList() {
        val response = "정리해드릴게요.\n- 파일 확인\n- 수정 방법 안내"

        assertThat(ResponseFormatComplianceChecker.hasFormatViolation(response)).isTrue()
    }

    @Test
    @DisplayName("코드펜스가 섞이면 위반이다")
    fun hasFormatViolation_shouldReturnTrueForCodeFence() {
        val response = "이렇게 하시면 돼요.\n```\nnpm install\n```"

        assertThat(ResponseFormatComplianceChecker.hasFormatViolation(response)).isTrue()
    }

    @Test
    @DisplayName("이모지가 섞이면 위반이다")
    fun hasFormatViolation_shouldReturnTrueForEmoji() {
        val response = "오늘도 좋은 하루 보내세요! 😊"

        assertThat(ResponseFormatComplianceChecker.hasFormatViolation(response)).isTrue()
    }
}
