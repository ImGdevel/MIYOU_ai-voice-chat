package com.miyou.app.application.monitoring.aop

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class MonitoredPipeline(
    val inputArgIndex: Int = 0,
)
