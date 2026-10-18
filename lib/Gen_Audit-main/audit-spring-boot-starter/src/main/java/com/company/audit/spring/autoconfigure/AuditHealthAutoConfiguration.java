package com.company.audit.spring.autoconfigure;

import com.company.audit.spring.health.AuditChainHealthIndicator;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import com.company.audit.spring.verification.ChainVerifierJob;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for {@link AuditChainHealthIndicator}.
 *
 * <p>{@code @ConditionalOnClass(HealthIndicator.class)} — that type lives in the
 * {@code spring-boot-health} module, which Boot 4 split out of {@code spring-boot-actuator}
 * entirely (confirmed by inspecting the resolved {@code spring-boot-actuator} jar and finding no
 * {@code org.springframework.boot.actuate.health} package at all, not assumed); pulled in
 * transitively by {@code spring-boot-starter-actuator}, not {@code spring-boot-actuator} alone.
 *
 * <p>Gated on {@link ChainVerifierJob} alone, not also on {@link AnchorPublisherJob}: verification
 * health is meaningful on its own in any deployment that never enables anchoring at all (no S3
 * bucket configured), so requiring both would needlessly narrow when this indicator is available.
 *
 * <p>Declares {@code after = AuditVerificationAutoConfiguration.class} — the class that actually
 * provides the {@link ChainVerifierJob} bean this configuration's own class-level
 * {@code @ConditionalOnBean} depends on, same reasoning already established for
 * {@code AuditAnchorAutoConfiguration}. This is a leaf: nothing else declares an {@code after=}
 * pointing at this class, so — unlike the Phase 6 regression — adding this edge cannot pull any
 * other auto-configuration out of place.
 */
@AutoConfiguration(after = AuditVerificationAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(ChainVerifierJob.class)
public class AuditHealthAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditHealthAutoConfiguration() {
    }

    /**
     * Provides the health indicator, unless a consuming application has already supplied its
     * own bean of this type.
     *
     * @param chainVerifierJob the scheduled verification job whose cached last summary is read
     * @param anchorPublisherJobProvider provides the scheduled anchoring job, if one is
     *     registered
     * @return a new health indicator
     */
    @Bean
    @ConditionalOnMissingBean(AuditChainHealthIndicator.class)
    public AuditChainHealthIndicator auditChainHealthIndicator(
            ChainVerifierJob chainVerifierJob, ObjectProvider<AnchorPublisherJob> anchorPublisherJobProvider) {
        return new AuditChainHealthIndicator(chainVerifierJob, anchorPublisherJobProvider);
    }
}
