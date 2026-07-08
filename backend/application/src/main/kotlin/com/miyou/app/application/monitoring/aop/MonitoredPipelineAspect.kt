package com.miyou.app.application.monitoring.aop

import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.monitoring.context.PipelineContext
import com.miyou.app.monitoring.monitor.DialoguePipelineMonitor
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * `DialoguePipelineUseCase` 구현체의 execute* 메서드를 시그니처(pointcut)로 직접
 * 잡아 트래커를 리액터 컨텍스트에 심는다. 예전엔 `@MonitoredPipeline` 애노테이션을
 * 비즈니스 코드에 붙여야 했는데, 이 아스펙트가 도메인 타입(`ConversationSession`)을
 * 알아도 되는 application 계층에 있으므로 애노테이션 없이 인자 위치(0=session,
 * 1=text)만으로 충분하다 - 비즈니스 로직이 모니터링을 전혀 몰라도 된다.
 *
 * monitoring 모듈 자체는 ArchUnit 규칙(monitoringShouldNotDependOnBusinessLayers)상
 * domain에 의존할 수 없어서, 도메인 타입을 다뤄야 하는 이 아스펙트는 monitoring이
 * 아니라 application 계층에 둔다 - `PipelineTracer`가 이미 같은 이유로 여기 있다.
 */
@Aspect
@Component
class MonitoredPipelineAspect(
    private val pipelineMonitor: DialoguePipelineMonitor,
) {
    @Around(
        "execution(* com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase+.execute*(..))",
    )
    fun wrapPipeline(joinPoint: ProceedingJoinPoint): Any? {
        val result = joinPoint.proceed()

        if (result !is Mono<*> && result !is Flux<*>) {
            return result
        }

        val session = joinPoint.args.getOrNull(0) as? ConversationSession ?: return result
        val text = joinPoint.args.getOrNull(1) as? String
        val tracker =
            pipelineMonitor.create(
                session.sessionId.value,
                session.userId,
                session.personaId.value,
                text,
            )

        return if (result is Mono<*>) {
            val withContext = result.contextWrite { context -> PipelineContext.withTracker(context, tracker) }
            tracker.attachLifecycle(withContext)
        } else {
            val flux = result as Flux<*>
            val withContext = flux.contextWrite { context -> PipelineContext.withTracker(context, tracker) }
            tracker.attachLifecycle(withContext)
        }
    }
}
