package com.miyou.app.application.dialogue.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SttPolicyTest {

	@Test
	@DisplayName("기본 언어는 공백을 제거해 정규화한다")
	void constructor_shouldTrimDefaultLanguage() {
		SttPolicy policy = new SttPolicy(1024L, " ko ");

		assertThat(policy.defaultLanguage()).isEqualTo("ko");
	}

	@Test
	@DisplayName("최대 파일 크기가 0 이하면 예외를 던진다")
	void constructor_shouldRejectNonPositiveMaxFileSizeBytes() {
		assertThatThrownBy(() -> new SttPolicy(0L, "ko"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxFileSizeBytes must be positive");
	}

	@Test
	@DisplayName("기본 언어가 비어 있으면 예외를 던진다")
	void constructor_shouldRejectBlankDefaultLanguage() {
		assertThatThrownBy(() -> new SttPolicy(1024L, "   "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("defaultLanguage must not be blank");
	}
}
