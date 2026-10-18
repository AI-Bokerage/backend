package com.company.audit.spring.autoconfigure;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.config.AuditProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration wiring {@code audit-core}'s public factories into Spring beans.
 */
@AutoConfiguration(after = AuditJpaAutoConfiguration.class)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditCoreAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditCoreAutoConfiguration() {
    }

    /**
     * Provides a default UTC system clock, overridable by a consuming application or test (for
     * example, with a {@link Clock#fixed}).
     *
     * @return the default clock bean
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Provides the {@link AuditAppender} bean, once a {@link ChainRepository} bean exists.
     *
     * @param chainRepository the chain repository to append through
     * @param clock the clock to timestamp appended records with
     * @param properties the starter's configuration properties
     * @return a new appender
     */
    @Bean
    @ConditionalOnBean(ChainRepository.class)
    public AuditAppender auditAppender(ChainRepository chainRepository, Clock clock, AuditProperties properties) {
        return AuditAppender.create(chainRepository, clock, properties.getJpa().getAppendMaxAttempts());
    }

    /**
     * Provides the {@link AuditVerifier} bean, once a {@link ChainRepository} bean exists.
     *
     * @param chainRepository the chain repository to verify against
     * @return a new verifier
     */
    @Bean
    @ConditionalOnBean(ChainRepository.class)
    public AuditVerifier auditVerifier(ChainRepository chainRepository) {
        return AuditVerifier.create(chainRepository);
    }
}
