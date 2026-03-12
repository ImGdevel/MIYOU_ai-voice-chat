package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.ExecuteAudioDialogueCommand;
import com.study.webflux.rag.domain.dialogue.model.ExecuteTextDialogueCommand;
import reactor.core.publisher.Flux;

/**
 * DialoguePipelineUseCase는 대화 파이프라인을 실행하는 인터페이스를 정의합니다.
 */
public interface DialoguePipelineUseCase {

	Flux<byte[]> executeAudioStreaming(ExecuteAudioDialogueCommand command);

	Flux<String> executeTextOnly(ExecuteTextDialogueCommand command);
}
