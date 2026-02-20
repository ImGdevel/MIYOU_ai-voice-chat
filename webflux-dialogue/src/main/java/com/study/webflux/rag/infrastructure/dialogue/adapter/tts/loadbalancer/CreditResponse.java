package com.study.webflux.rag.infrastructure.dialogue.adapter.tts.loadbalancer;

/**
 * Supertone API /v1/credits 응답
 *
 * @param credits
 *            남은 크레딧
 */
public record CreditResponse(
	double credits
) {
}
