package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.AudioTranscriptionInput;
import reactor.core.publisher.Mono;

/** 음성 데이터를 텍스트로 변환하는 STT 포트입니다. */
public interface SttPort {

	/** 오디오 데이터를 텍스트로 변환합니다. */
	Mono<String> transcribe(AudioTranscriptionInput input);
}
