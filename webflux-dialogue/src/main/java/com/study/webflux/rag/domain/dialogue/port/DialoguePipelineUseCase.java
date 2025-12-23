package com.study.webflux.rag.domain.dialogue.port;

import reactor.core.publisher.Flux;

/**
 * DialoguePipelineUseCase는 대화 파이프라인을 실행하는 인터페이스를 정의합니다.
 */
public interface DialoguePipelineUseCase {
	/**
	 * 대화 파이프라인을 실행하고 텍스트 스트림을 반환합니다.
	 *
	 * @param text
	 *            대화 텍스트
	 * @return 텍스트 스트림
	 */
	Flux<String> executeStreaming(String text);

	/**
	 * 대화 파이프라인을 실행하고 오디오 스트림을 반환합니다.
	 *
	 * @param text
	 *            대화 텍스트
	 * @return 오디오 스트림
	 */
	Flux<byte[]> executeAudioStreaming(String text);

	/**
	 * 대화 파이프라인을 실행하고 텍스트 스트림을 반환합니다.
	 *
	 * @param text
	 *            대화 텍스트
	 * @return 텍스트 스트림
	 */
	Flux<String> executeTextOnly(String text);
}
