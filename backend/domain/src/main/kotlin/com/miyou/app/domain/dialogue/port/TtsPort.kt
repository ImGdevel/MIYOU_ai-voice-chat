package com.miyou.app.domain.dialogue.port

import com.miyou.app.domain.dialogue.model.TtsCommand
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * TTS(음성 합성) 서비스와의 통신을 추상화한 아웃포트 인터페이스.
 */
interface TtsPort {
    /**
     * 주어진 텍스트를 음성으로 합성하여 실시간 오디오 데이터 스트림으로 반환합니다.
     *
     * @param command TTS 합성 명령 정보 (텍스트, 목소리 설정 등)
     * @return 바이너리 오디오 데이터 스트림 (Flux)
     */
    fun streamSynthesize(command: TtsCommand): Flux<ByteArray>

    /**
     * 주어진 텍스트를 음성으로 합성하여 전체 오디오 바이너리 데이터를 일괄 반환합니다.
     *
     * @param command TTS 합성 명령 정보
     * @return 전체 오디오 바이너리 데이터 (Mono)
     */
    fun synthesize(command: TtsCommand): Mono<ByteArray>

    /**
     * TTS 엔진의 접속 초기화 및 준비 상태를 활성화합니다.
     */
    fun prepare(): Mono<Void> = Mono.empty()
}
