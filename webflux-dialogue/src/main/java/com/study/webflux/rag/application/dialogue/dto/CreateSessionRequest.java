package com.study.webflux.rag.application.dialogue.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
	@NotBlank(message = "userId는 필수입니다") String userId,
	String personaId
) {
}
