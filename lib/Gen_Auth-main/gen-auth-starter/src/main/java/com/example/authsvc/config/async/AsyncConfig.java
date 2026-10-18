package com.example.authsvc.config.async;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration for non-critical auth side-effects.
 *
 * <p>The {@code authAsync} executor handles fire-and-forget operations that must
 * not block the authentication response: audit logging, login-attempt persistence,
 * PG refresh-token archival, and RabbitMQ event publishing.
 *
 * <p>Sizing rationale:
 * <ul>
 *   <li>Core=4 — enough threads for steady-state burst without idle CPU waste.</li>
 *   <li>Max=16 — headroom for traffic spikes; bounded to prevent runaway threads.</li>
 *   <li>Queue=200 — absorbs transient DB/MQ slowness without rejecting tasks.</li>
 *   <li>{@link ThreadPoolExecutor.CallerRunsPolicy} — under extreme saturation,
 *       executes on the calling thread rather than silently dropping tasks,
 *       preserving audit-log guarantees.</li>
 * </ul>
 *
 * <p>Observability additions (production hardening):
 * <ul>
 *   <li>{@link MdcTaskDecorator} — propagates MDC context (userId, tenantId,
 *       requestId) from the submitting request thread into every async task,
 *       enabling full log correlation across thread boundaries.</li>
 *   <li>{@link ExecutorServiceMetrics} — registers active thread count, queue
 *       depth, completed task count, and rejection count with Micrometer so
 *       they appear under {@code /actuator/metrics} and Prometheus.</li>
 *   <li>{@link AsyncUncaughtExceptionHandler} — surfaces uncaught exceptions from
 *       {@code @Async} void methods as structured ERROR logs instead of silently
 *       discarding them.</li>
 *   <li>Graceful shutdown — waits up to 30 s for in-flight tasks before JVM exit
 *       so audit writes and MQ publishes are not abruptly abandoned.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final MeterRegistry meterRegistry;

    @Bean("authAsync")
    public Executor authAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("auth-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Propagate MDC keys (userId, tenantId, requestId, traceId) from the
        // submitting request thread into every async worker. Without this,
        // async log lines are impossible to correlate in production.
        executor.setTaskDecorator(new MdcTaskDecorator());

        // Graceful shutdown: wait for in-flight audit writes and MQ publishes
        // before the JVM exits. 30 s is generous; most tasks complete in < 200 ms.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        // Register pool metrics with Micrometer. Exposes:
        //   executor.active          — threads currently running tasks
        //   executor.queued          — tasks waiting in queue
        //   executor.completed       — total tasks finished
        //   executor.pool.size       — current pool size
        new ExecutorServiceMetrics(
                executor.getThreadPoolExecutor(),
                "auth.async",
                Tags.empty()
        ).bindTo(meterRegistry);

        log.info("async.executor.initialized pool=authAsync core=4 max=16 queue=200 "
                + "mdc_propagation=enabled graceful_shutdown=30s metrics=registered");
        return executor;
    }

    /**
     * Handles uncaught exceptions thrown by {@code @Async} void methods.
     *
     * <p>Spring's async infrastructure silently discards these by default.
     * Surfacing them as ERROR logs ensures failures in audit logging,
     * login-attempt recording, and similar side-effects are visible in
     * operational dashboards without ever failing the HTTP request.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            String userId   = MDC.get("userId");
            String tenantId = MDC.get("tenantId");
            log.error("async.uncaught_exception method={}.{} exceptionType={} message={} "
                            + "userId={} tenantId={}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    userId,
                    tenantId,
                    ex);
        };
    }
}

