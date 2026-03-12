package com.study.webflux.rag.domain.monitoring.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageSnapshotTest {

	@Test
	@DisplayName("attributes는 null 이어도 빈 맵으로 정규화한다")
	void constructor_shouldNormalizeNullAttributesToEmptyMap() {
		StageSnapshot snapshot = new StageSnapshot(DialoguePipelineStage.RETRIEVAL,
			StageStatus.COMPLETED,
			Instant.now(),
			Instant.now(),
			10L,
			null);

		assertThat(snapshot.attributes()).isEmpty();
	}

	@Test
	@DisplayName("attributes는 외부 변경에 오염되지 않는 불변 맵으로 보관한다")
	void constructor_shouldDefensivelyCopyAttributes() {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("documentCount", 3);

		StageSnapshot snapshot = new StageSnapshot(DialoguePipelineStage.RETRIEVAL,
			StageStatus.COMPLETED,
			Instant.now(),
			Instant.now(),
			10L,
			source);

		source.put("documentCount", 9);
		source.put("memoryCount", 2);

		assertThat(snapshot.attributes())
			.containsEntry("documentCount", 3)
			.doesNotContainKey("memoryCount");
		assertThatThrownBy(() -> snapshot.attributes().put("another", 1))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
