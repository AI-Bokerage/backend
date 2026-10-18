package com.company.audit.spring.autoconfigure;

import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.metrics.MicrometerAuditMetricsRecorder;
import com.company.audit.spring.metrics.NoOpAuditMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the single {@link AuditMetricsRecorder} bean every other call site in
 * this starter depends on.
 *
 * <p>Unconditional at the class level, unlike every other auto-configuration in this starter: an
 * {@link AuditMetricsRecorder} bean must always exist, since
 * {@link com.company.audit.spring.messaging.RabbitEventConsumer},
 * {@link com.company.audit.spring.persistence.jpa.adapter.JpaChainRepository},
 * {@link com.company.audit.spring.verification.ChainVerifierJob}, and
 * {@link com.company.audit.spring.scheduling.AnchorPublisherJob} all take it as a required
 * constructor dependency, with no {@code @ConditionalOnBean} gate of their own on it — the
 * {@link NoOpAuditMetricsRecorder} default guarantees that requirement is always satisfiable.
 *
 * <p>The Micrometer-aware bean lives in the nested {@link MicrometerConfiguration}, gated by its
 * own {@code @ConditionalOnClass(MeterRegistry.class)}, and — because nested {@code @Configuration}
 * classes are processed before the enclosing class's own {@code @Bean} methods — wins the
 * {@code @ConditionalOnMissingBean} check below whenever Micrometer and a {@code MeterRegistry}
 * bean are both present, same ordering already relied on by
 * {@code AuditRabbitAutoConfiguration.MicrometerConfiguration}.
 */
@AutoConfiguration
public class AuditMetricsAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditMetricsAutoConfiguration() {
    }

    /**
     * Provides the no-op default, unless a consuming application has already supplied its own
     * {@link AuditMetricsRecorder} — including the Micrometer-backed one
     * {@link MicrometerConfiguration} provides when applicable.
     *
     * @return a new no-op recorder
     */
    @Bean
    @ConditionalOnMissingBean(AuditMetricsRecorder.class)
    public NoOpAuditMetricsRecorder noOpAuditMetricsRecorder() {
        return new NoOpAuditMetricsRecorder();
    }

    /**
     * Provides the Micrometer-backed {@link AuditMetricsRecorder} bean when Micrometer is on the
     * classpath and a {@link MeterRegistry} bean exists. Isolated in its own nested class so
     * that this class's bytecode — and any method signature naming {@link MeterRegistry} — is
     * never loaded when Micrometer is genuinely absent; see {@link AuditMetricsRecorder}'s
     * Javadoc.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerConfiguration {

        /**
         * Provides the Micrometer-backed recorder.
         *
         * @param meterRegistry the registry to record every metric to
         * @return a new recorder
         */
        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(AuditMetricsRecorder.class)
        public MicrometerAuditMetricsRecorder micrometerAuditMetricsRecorder(MeterRegistry meterRegistry) {
            return new MicrometerAuditMetricsRecorder(meterRegistry);
        }
    }
}
