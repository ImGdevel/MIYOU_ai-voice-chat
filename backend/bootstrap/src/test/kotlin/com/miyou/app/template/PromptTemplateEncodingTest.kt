package com.miyou.app.template

import com.miyou.app.infrastructure.common.template.FileBasedPromptTemplate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 실제 templates/ 리소스 파일을 mock 없이 로드해서 인코딩이 깨지지 않았는지 검증한다.
 * (2026-07-08 UTF-16LE로 저장된 파일을 UTF-8로 읽어 깨진 텍스트가 나가던 버그의 회귀 테스트)
 */
class PromptTemplateEncodingTest {
    private val loader = FileBasedPromptTemplate()

    @ParameterizedTest
    @CsvSource(
        "system/common, 마크다운",
        "system/persona/maid, 리리아",
        "system/persona/interviewer, 면접관",
        "dialogue/conversation, 어시스턴트",
        "memory/extraction-system, EXPERIENTIAL",
    )
    @DisplayName("템플릿 파일이 깨지지 않고 예상 키워드를 포함한다")
    fun load_shouldDecodeWithoutReplacementCharacter(
        templateName: String,
        expectedKeyword: String,
    ) {
        val content = loader.load(templateName)

        assertThat(content).doesNotContain("�")
        assertThat(content).contains(expectedKeyword)
    }
}
