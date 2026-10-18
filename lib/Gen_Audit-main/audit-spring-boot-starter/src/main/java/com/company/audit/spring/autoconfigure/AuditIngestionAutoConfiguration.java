package com.company.audit.spring.autoconfigure;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.port.EventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for {@link AuditRecorder}.
 *
 * <p>Requires both an {@link AuditAppender} and an {@link EventStore} bean to be present. A
 * consumer with only the JPA adapter on its classpath (no Mongo) gets an {@code AuditAppender}
 * bean from {@link AuditCoreAutoConfiguration} but no {@link AuditRecorder}, since the latter
 * genuinely needs both the ledger and the rich event store to do anything useful.
 */
@AutoConfiguration(after = {AuditJpaAutoConfiguration.class, AuditMongoAutoConfiguration.class})
@ConditionalOnBean({AuditAppender.class, EventStore.class})
public class AuditIngestionAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditIngestionAutoConfiguration() {
    }

    /**
     * Provides the {@link AuditRecorder} bean.
     *
     * @param partitionRegistry resolves the stable partition context for an event's partition
     * @param auditAppender appends the event to the tamper-evident ledger
     * @param eventStore persists the full event after the ledger append commits
     * @return a new recorder
     */
    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    public AuditRecorder auditRecorder(
            PartitionRegistry partitionRegistry, AuditAppender auditAppender, EventStore eventStore) {
        return new AuditRecorder(partitionRegistry, auditAppender, eventStore);
    }
}
