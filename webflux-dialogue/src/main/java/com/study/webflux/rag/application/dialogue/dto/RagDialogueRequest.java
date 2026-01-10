package com.study.webflux.rag.application.dialogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(description = "대화 Request")
public record RagDialogueRequest(
	@Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
	@NotBlank String userId,

	@Schema(description = "유저 프롬포트", example = "안녕하세요")
	@NotBlank String text,

	@Schema(description = "요청시각", example = "2024-12-21T12:00:00Z")
	@NotNull Instant requestedAt
) {
}
