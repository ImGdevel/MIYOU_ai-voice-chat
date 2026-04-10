package com.miyou.app.domain.monitoring.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class StageSnapshotTest {
    @Test
    @DisplayName("속성 맵이 비어 있어도 스냅샷을 생성할 수 있다")
    fun constructor_shouldAllowEmptyAttributes() {
        val snapshot =
            StageSnapshot(
                DialoguePipelineStage.RETRIEVAL,
                StageStatus.COMPLETED,
                Instant.now(),
                Instant.now(),
                10L,
                emptyMap(),
            )

        assertThat(snapshot.attributes).isEmpty()
    }

    @Test
    @DisplayName("전달한 속성 맵을 그대로 보존한다")
    fun constructor_shouldPreserveAttributes() {
        val snapshot =
            StageSnapshot(
                DialoguePipelineStage.RETRIEVAL,
                StageStatus.COMPLETED,
                Instant.now(),
                Instant.now(),
                10L,
                linkedMapOf("documentCount" to 3, "memoryCount" to 2),
            )

        assertThat(snapshot.attributes)
            .containsEntry("documentCount", 3)
            .containsEntry("memoryCount", 2)
    }
}
