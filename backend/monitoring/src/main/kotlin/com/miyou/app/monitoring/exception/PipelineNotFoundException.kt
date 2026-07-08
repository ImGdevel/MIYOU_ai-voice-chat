package com.miyou.app.monitoring.exception

class PipelineNotFoundException(
    val pipelineId: String,
) : RuntimeException("Pipeline not found: $pipelineId")
