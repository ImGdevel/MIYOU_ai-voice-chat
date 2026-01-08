package com.study.webflux.rag.domain.memory.model;

/** 기억 유형을 분류합니다. */
public enum MemoryType {
	/** 대화 경험에서 추출한 기억입니다. */
	EXPERIENTIAL,

	/** 사실 기반으로 추출한 기억입니다. */
	FACTUAL
}
