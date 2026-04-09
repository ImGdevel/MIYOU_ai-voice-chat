package com.miyou.app.domain.monitoring.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.LinkedHashMap

class StageSnapshotTest {
    @Test
    @DisplayName("attributes가 null이어도 빈 맵으로 정규화된다")
    fun constructor_shouldNormalizeNullAttributesToEmptyMap() {
        val snapshot =
            StageSnapshot(
                DialoguePipelineStage.RETRIEVAL,
                StageStatus.COMPLETED,
                Instant.now(),
                Instant.now(),
                10L,
                null,
            )

        assertThat(snapshot.attributes()).isEmpty()
    }

    @Test
    @DisplayName("attributes는 외부 변경에 영향을 받지 않는 불변 맵으로 보관된다")
    fun constructor_shouldDefensivelyCopyAttributes() {
        val source = LinkedHashMap<String, Any>()
        source["documentCount"] = 3

        val snapshot =
            StageSnapshot(
                DialoguePipelineStage.RETRIEVAL,
                StageStatus.COMPLETED,
                Instant.now(),
                Instant.now(),
                10L,
                source,
            )

        source["documentCount"] = 9
        source["memoryCount"] = 2

        assertThat(snapshot.attributes())
            .containsEntry("documentCount", 3)
            .doesNotContainKey("memoryCount")
        assertThatThrownBy { snapshot.attributes().put("another", 1) }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }
}
