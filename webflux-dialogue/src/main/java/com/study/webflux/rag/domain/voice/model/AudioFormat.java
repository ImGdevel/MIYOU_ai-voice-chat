package com.study.webflux.rag.domain.voice.model;

/** TTS 합성 음성 출력의 오디오 형식을 정의합니다. */
public enum AudioFormat {
	/** PCM 기반 WAV 형식입니다. */
	WAV("audio/wav"),
	/** 손실 압축된 MP3 형식입니다. */
	MP3("audio/mpeg"),
	/** 비압축 PCM 형식입니다. */
	PCM("audio/pcm");

	/** 오디오 형식의 HTTP 미디어 타입입니다. */
	private final String mediaType;

	/** 미디어 타입으로 오디오 형식을 초기화합니다. */
	AudioFormat(String mediaType) {
		this.mediaType = mediaType;
	}

	/** 이 형식에 대응하는 HTTP 미디어 타입을 반환합니다. */
	public String getMediaType() {
		return mediaType;
	}

	/** 문자열로부터 AudioFormat을 파싱합니다. */
	public static AudioFormat fromString(String format) {
		return valueOf(format.toUpperCase());
	}
}
