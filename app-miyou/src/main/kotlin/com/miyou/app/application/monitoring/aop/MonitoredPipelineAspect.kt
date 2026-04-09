package com.miyou.app.application.monitoring.aop

import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.monitor.DialoguePipelineMonitor
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Aspect
@Component
class MonitoredPipelineAspect(
    private val pipelineMonitor: DialoguePipelineMonitor,
) {
    private val logger = LoggerFactory.getLogger(MonitoredPipelineAspect::class.java)

    @Around("@annotation(monitoredPipeline)")
    fun wrapPipeline(
        joinPoint: ProceedingJoinPoint,
        monitoredPipeline: MonitoredPipeline,
    ): Any {
        val result = joinPoint.proceed()

        if (result !is Mono<*> && result !is Flux<*>) {
            return result
        }

        val inputText = resolveInputText(joinPoint.args, monitoredPipeline)
        val tracker = pipelineMonitor.create(inputText)

        return if (result is Mono<*>) {
            val withContext =
                result.contextWrite { context ->
                    PipelineContext.withTracker(context, tracker)
                }
            tracker.attachLifecycle(withContext)
        } else {
            val flux = result as Flux<*>
            val withContext =
                flux.contextWrite { context ->
                    PipelineContext.withTracker(context, tracker)
                }
            tracker.attachLifecycle(withContext)
        }
    }

    private fun resolveInputText(
        args: Array<Any>,
        monitoredPipeline: MonitoredPipeline,
    ): String {
        val index = monitoredPipeline.inputArgIndex
        if (args.isEmpty() || index < 0 || index >= args.size) {
            logger.debug("Monitored pipeline input arg index {} is out of bounds.", index)
            return ""
        }

        val candidate = args[index]
        return when {
            candidate == null -> ""
            candidate is String -> candidate
            else -> candidate.toString()
        }
    }
}
