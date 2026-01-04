package com.study.webflux.rag.application.monitoring.context;

import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public final class PipelineContext {

	public static final Object TRACKER_KEY = new Object();

	private PipelineContext() {
	}

	public static Context withTracker(Context context, DialoguePipelineTracker tracker) {
		return context.put(TRACKER_KEY, tracker);
	}

	public static DialoguePipelineTracker requireTracker(ContextView contextView) {
		return contextView.get(TRACKER_KEY);
	}

	public static DialoguePipelineTracker findTracker(ContextView contextView) {
		if (contextView.hasKey(TRACKER_KEY)) {
			return contextView.get(TRACKER_KEY);
		}
		return null;
	}
}
