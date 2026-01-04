package com.study.webflux.rag.application.monitoring.aop;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.application.monitoring.context.PipelineContext;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineMonitor;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Aspect
@Component
public class MonitoredPipelineAspect {

	private final DialoguePipelineMonitor pipelineMonitor;

	public MonitoredPipelineAspect(DialoguePipelineMonitor pipelineMonitor) {
		this.pipelineMonitor = pipelineMonitor;
	}

	@Around("@annotation(monitoredPipeline)")
	public Object wrapPipeline(ProceedingJoinPoint joinPoint,
		MonitoredPipeline monitoredPipeline) throws Throwable {
		Object result = joinPoint.proceed();

		if (!(result instanceof Mono<?> || result instanceof Flux<?>)) {
			return result;
		}

		String inputText = resolveInputText(joinPoint.getArgs(), monitoredPipeline);
		DialoguePipelineTracker tracker = pipelineMonitor.create(inputText);

		if (result instanceof Mono<?> mono) {
			Mono<?> withContext = mono.contextWrite(ctx -> PipelineContext.withTracker(ctx,
				tracker));
			return tracker.attachLifecycle(withContext);
		}

		Flux<?> flux = (Flux<?>) result;
		Flux<?> withContext = flux.contextWrite(ctx -> PipelineContext.withTracker(ctx, tracker));
		return tracker.attachLifecycle(withContext);
	}

	private String resolveInputText(Object[] args, MonitoredPipeline monitoredPipeline) {
		int index = monitoredPipeline.inputArgIndex();
		if (args == null || index < 0 || index >= args.length) {
			log.debug("Monitored pipeline input arg index {} is out of bounds.", index);
			return "";
		}
		Object candidate = args[index];
		if (candidate == null) {
			return "";
		}
		if (candidate instanceof String text) {
			return text;
		}
		return String.valueOf(candidate);
	}
}
