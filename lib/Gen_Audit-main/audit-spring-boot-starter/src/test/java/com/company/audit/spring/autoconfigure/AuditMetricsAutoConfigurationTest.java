package com.company.audit.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.metrics.MicrometerAuditMetricsRecorder;
import com.company.audit.spring.metrics.NoOpAuditMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proves — with a real {@code ApplicationContextRunner}, not merely by reading the
 * {@code @ConditionalOnClass} annotation — that {@link NoOpAuditMetricsRecorder} is the only
 * {@link AuditMetricsRecorder} bean when Micrometer is absent from the classpath, and that
 * {@link MicrometerAuditMetricsRecorder} takes over instead whenever Micrometer and a
 * {@link MeterRegistry} bean are both present.
 */
class AuditMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AuditMetricsAutoConfiguration.class));

    @Test
    void withMicrometerAbsentOnlyNoOpRecorderExists() {
        runner.withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(context -> {
            assertThat(context).hasSingleBean(AuditMetricsRecorder.class);
            assertThat(context).hasSingleBean(NoOpAuditMetricsRecorder.class);
            assertThat(context).doesNotHaveBean(MicrometerAuditMetricsRecorder.class);
        });
    }

    @Test
    void withMicrometerPresentButNoMeterRegistryBeanOnlyNoOpRecorderExists() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AuditMetricsRecorder.class);
            assertThat(context).hasSingleBean(NoOpAuditMetricsRecorder.class);
            assertThat(context).doesNotHaveBean(MicrometerAuditMetricsRecorder.class);
        });
    }

    @Test
    void withMicrometerAndMeterRegistryPresentMicrometerRecorderIsUsedInstead() {
        runner.withUserConfiguration(FakeMeterRegistryConfig.class).run(context -> {
            assertThat(context).hasSingleBean(AuditMetricsRecorder.class);
            assertThat(context).hasSingleBean(MicrometerAuditMetricsRecorder.class);
            assertThat(context).doesNotHaveBean(NoOpAuditMetricsRecorder.class);
        });
    }

    @Configuration
    static class FakeMeterRegistryConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
