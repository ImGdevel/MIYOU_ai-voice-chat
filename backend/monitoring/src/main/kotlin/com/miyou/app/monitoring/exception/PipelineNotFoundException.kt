package com.miyou.app.monitoring.exception

import com.miyou.app.exception.BusinessException
import com.miyou.app.exception.CommonErrorCode

class PipelineNotFoundException(
    val pipelineId: String,
) : BusinessException(
        errorCode = CommonErrorCode.PIPELINE_NOT_FOUND,
        message = "${CommonErrorCode.PIPELINE_NOT_FOUND.message} ($pipelineId)",
        details = mapOf("pipelineId" to pipelineId)
    )
