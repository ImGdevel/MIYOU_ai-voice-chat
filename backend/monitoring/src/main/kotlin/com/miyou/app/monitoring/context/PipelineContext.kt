package com.miyou.app.monitoring.context

import com.miyou.app.monitoring.monitor.DialoguePipelineTracker
import reactor.util.context.Context
import reactor.util.context.ContextView

object PipelineContext {
    val TRACKER_KEY: Any = Any()

    fun withTracker(
        context: Context,
        tracker: DialoguePipelineTracker,
    ): Context = context.put(TRACKER_KEY, tracker)

    fun requireTracker(contextView: ContextView): DialoguePipelineTracker = contextView.get(TRACKER_KEY)

    fun findTracker(contextView: ContextView): DialoguePipelineTracker? =
        if (contextView.hasKey(TRACKER_KEY)) {
            contextView.get(TRACKER_KEY)
        } else {
            null
        }
}
