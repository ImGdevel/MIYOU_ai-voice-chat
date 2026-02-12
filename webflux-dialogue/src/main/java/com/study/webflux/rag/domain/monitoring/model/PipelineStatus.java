package com.study.webflux.rag.domain.monitoring.model;

/** 대화 파이프라인 전체 실행 상태를 나타냅니다. */
public enum PipelineStatus {
	/** 파이프라인이 실행 중입니다. */
	RUNNING,

	/** 성공적으로 완료되었습니다. */
	COMPLETED,

	/** 오류로 인해 실패했습니다. */
	FAILED,

	/** 수동으로 취소되었습니다. */
	CANCELLED
}
