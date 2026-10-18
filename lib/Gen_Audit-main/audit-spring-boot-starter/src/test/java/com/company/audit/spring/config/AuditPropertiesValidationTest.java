package com.company.audit.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Proves that {@link AuditProperties}'s {@code @Validated} annotation actually does something —
 * given a JSR-380 validator is on this test's classpath (this module's own
 * {@code spring-boot-starter-validation} {@code testImplementation} dependency) — rather than
 * merely asserting the annotation is present. See {@link AuditProperties}'s Javadoc for the
 * explicit caveat that without a validator on the classpath, none of this fires at all.
 */
class AuditPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableAuditPropertiesConfig.class);

    @Test
    void bucketLeftEntirelyUnsetDoesNotFailStartup() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bucketExplicitlySetToBlankFailsFastWithClearMessage() {
        runner.withPropertyValues("audit.anchor.bucket=   ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseMessage(context.getStartupFailure())).contains("bucket");
        });
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    @Test
    void bucketSetToARealValuePassesValidation() {
        runner.withPropertyValues("audit.anchor.bucket=my-real-bucket").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AuditProperties.class).getAnchor().getBucket()).isEqualTo("my-real-bucket");
        });
    }

    @Test
    void defaultShardConfigurationDoesNotFailStartup() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void totalInstancesSetToZeroFailsFastWithClearMessageInsteadOfArithmeticExceptionAtRuntime() {
        runner.withPropertyValues("audit.rabbit.shard.total-instances=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseMessage(context.getStartupFailure())).contains("totalInstances");
        });
    }

    @Test
    void instanceIndexSetToNegativeFailsFastWithClearMessage() {
        runner.withPropertyValues("audit.rabbit.shard.instance-index=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCauseMessage(context.getStartupFailure())).contains("instanceIndex");
        });
    }

    @Configuration
    @EnableConfigurationProperties(AuditProperties.class)
    static class EnableAuditPropertiesConfig {
    }
}
