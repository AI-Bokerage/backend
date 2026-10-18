package com.company.audit.spring.autoconfigure;

import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.config.AuditProperties;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.persistence.jpa.adapter.JpaChainRepository;
import com.company.audit.spring.persistence.jpa.adapter.JpaPartitionRegistry;
import com.company.audit.spring.persistence.jpa.mapper.AuditImmutableMapper;
import com.company.audit.spring.persistence.jpa.repository.SpringDataAuditImmutableRepository;
import com.company.audit.spring.persistence.jpa.repository.SpringDataChainPartitionRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for the JPA-backed {@code audit-core} port implementations.
 *
 * <p>Every internal adapter/mapper bean is declared explicitly via {@code @Bean} methods here
 * rather than relying on the consuming application's component scan to reach into this starter's
 * internal packages — {@code @Component}-scanning a library's own implementation classes is
 * fragile, since it only works if the host application happens to scan this package.
 *
 * <p>Registers this starter's own Flyway migration location ({@code classpath:db/migration/audit})
 * additively, without replacing any location a consuming application has already configured for
 * its own migrations.
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@ConditionalOnProperty(prefix = "audit.jpa", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
@EnableJpaRepositories(basePackages = "com.company.audit.spring.persistence.jpa.repository")
@EntityScan(basePackages = "com.company.audit.spring.persistence.jpa.entity")
public class AuditJpaAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditJpaAutoConfiguration() {
    }

    /**
     * Provides the mapper bean between {@code audit-core} domain types and JPA entities.
     *
     * @return a new mapper
     */
    @Bean
    public AuditImmutableMapper auditImmutableMapper() {
        return new AuditImmutableMapper();
    }

    /**
     * Provides the JPA-backed {@link ChainRepository} adapter, unless a consuming application has
     * already supplied its own {@link ChainRepository} bean.
     *
     * @param springDataRepository the underlying Spring Data repository
     * @param mapper the domain/entity mapper
     * @param metricsRecorder records append outcomes and timing
     * @param properties the starter's configuration properties
     * @return a new adapter, exposed under both its concrete type and {@link ChainRepository}
     */
    @Bean
    @ConditionalOnMissingBean(ChainRepository.class)
    public JpaChainRepository jpaChainRepository(
            SpringDataAuditImmutableRepository springDataRepository,
            AuditImmutableMapper mapper,
            AuditMetricsRecorder metricsRecorder,
            AuditProperties properties) {
        return new JpaChainRepository(
                springDataRepository, mapper, metricsRecorder, properties.getJpa().getPartitionLockTimeout());
    }

    /**
     * Provides the JPA-backed {@link PartitionRegistry} adapter, unless a consuming application
     * has already supplied its own {@link PartitionRegistry} bean.
     *
     * @param repository the underlying Spring Data repository
     * @param clock the clock used to stamp a partition's creation instant on first resolution
     * @return a new adapter, exposed under both its concrete type and {@link PartitionRegistry}
     */
    @Bean
    @ConditionalOnMissingBean(PartitionRegistry.class)
    public JpaPartitionRegistry jpaPartitionRegistry(SpringDataChainPartitionRepository repository, Clock clock) {
        return new JpaPartitionRegistry(repository, clock);
    }

    /**
     * Registers this starter's Flyway migration location additively.
     *
     * @return a customizer that appends {@code classpath:db/migration/audit} to the existing
     *     configured locations
     */
    @Bean
    public FlywayConfigurationCustomizer auditFlywayLocationCustomizer() {
        return configuration -> {
            String[] existing = configuration.getLocations() == null
                    ? new String[0]
                    : Arrays.stream(configuration.getLocations()).map(Object::toString).toArray(String[]::new);
            String[] combined = new String[existing.length + 1];
            System.arraycopy(existing, 0, combined, 0, existing.length);
            combined[existing.length] = "classpath:db/migration/audit";
            configuration.locations(combined);
        };
    }
}
