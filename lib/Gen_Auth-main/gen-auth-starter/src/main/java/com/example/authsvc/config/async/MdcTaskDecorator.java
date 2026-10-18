package com.example.authsvc.config.async;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * {@link TaskDecorator} that propagates the parent thread's MDC context map
 * into async worker threads managed by the {@code authAsync} executor.
 *
 * <p>Without this, all log statements inside {@code asyncExecutor.execute(...)}
 * lambdas and {@code @Async} methods lose tracing keys such as {@code requestId},
 * {@code userId}, and {@code tenantId}, making log correlation impossible.
 *
 * <p>The captured context map is a <em>snapshot</em> taken on the submitting
 * (request) thread, so parent and child threads have independent MDC state.
 * After the task completes, the async thread's MDC is fully restored to whatever
 * state it held before — important for virtual-thread and thread-pool reuse.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Snapshot taken on the request thread before the task is submitted.
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            // Save whatever MDC state the async thread already had (pooled threads
            // may carry leftover state from a previous task).
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                // Always restore — ensures no MDC bleed between consecutive tasks
                // scheduled on the same thread.
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
