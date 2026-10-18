package com.company.audit.spring.autoconfigure;

import com.company.audit.spring.persistence.mongo.adapter.MongoEventStore;
import com.company.audit.spring.persistence.mongo.document.AuditEventDocument;
import com.company.audit.spring.persistence.mongo.mapper.AuditEventMongoMapper;
import com.company.audit.spring.persistence.mongo.repository.SpringDataAuditEventMongoRepository;
import com.company.audit.spring.port.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Auto-configuration for the Mongo-backed rich event store.
 *
 * <p>Every internal adapter/mapper bean is declared explicitly via {@code @Bean} methods, for
 * the same reason as {@link AuditJpaAutoConfiguration}: a starter should not rely on the
 * consuming application's component scan reaching into its internal packages.
 */
@AutoConfiguration
@ConditionalOnClass(MongoRepository.class)
@EnableMongoRepositories(basePackages = "com.company.audit.spring.persistence.mongo.repository")
public class AuditMongoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditMongoAutoConfiguration.class);

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditMongoAutoConfiguration() {
    }

    /**
     * Provides the mapper bean between {@code audit-core} domain types and Mongo documents.
     *
     * @return a new mapper
     */
    @Bean
    public AuditEventMongoMapper auditEventMongoMapper() {
        return new AuditEventMongoMapper();
    }

    /**
     * Provides the Mongo-backed {@link EventStore} adapter, unless a consuming application has
     * already supplied its own {@link EventStore} bean.
     *
     * @param springDataRepository the underlying Spring Data repository
     * @param mapper the domain/document mapper
     * @return a new adapter, exposed under both its concrete type and {@link EventStore}
     */
    @Bean
    @ConditionalOnMissingBean(EventStore.class)
    public MongoEventStore mongoEventStore(
            SpringDataAuditEventMongoRepository springDataRepository, AuditEventMongoMapper mapper) {
        return new MongoEventStore(springDataRepository, mapper);
    }

    /**
     * Explicitly creates the indexes {@link AuditEventDocument}'s {@code @Indexed} fields
     * declare.
     *
     * <p>{@code @Indexed} is source-level metadata only: Spring Data Mongo does not act on it
     * unless {@code spring.data.mongodb.auto-index-creation=true} is set on the *consuming*
     * application — a global property this starter has no business setting on a consumer's
     * behalf, since it would also apply to every other Mongo document that application defines,
     * not just this one. Resolving and creating only this document's own indexes here, scoped to
     * exactly the collection this starter owns, gets the same real-index guarantee without that
     * side effect — found via a release-readiness audit that queried the real, running
     * collection's index catalog and found only the default {@code _id} index present, not
     * assumed correct because the annotation was there.
     *
     * <p><b>Deliberately non-fatal.</b> A first attempt let any failure (most obviously, Mongo
     * being unreachable at the exact moment this starter's context starts) propagate and fail the
     * <em>entire</em> application context — found by this same release-readiness audit, when it
     * broke every load test that boots a context without a real Mongo connection configured,
     * because {@link AuditMongoAutoConfiguration} only conditions on {@code MongoRepository} being
     * on the classpath, never on Mongo actually being reachable. An index is a performance aid,
     * not a correctness guarantee this library depends on — {@link MongoEventStore} still works
     * correctly, just slower, without one — so a failure here is caught and logged at
     * {@code WARN}, not allowed to block startup.
     *
     * @param mongoTemplate the auto-configured Mongo template
     * @return an initializer that best-effort ensures {@link AuditEventDocument}'s indexes exist
     */
    @Bean
    public InitializingBean auditEventDocumentIndexInitializer(MongoTemplate mongoTemplate) {
        return () -> {
            try {
                var indexOps = mongoTemplate.indexOps(AuditEventDocument.class);
                IndexResolver.create(mongoTemplate.getConverter().getMappingContext())
                        .resolveIndexFor(AuditEventDocument.class)
                        .forEach(indexOps::createIndex);
            } catch (RuntimeException e) {
                log.warn(
                        "Could not create audit_events indexes at startup; continuing without them "
                                + "(queries will still be correct, just unindexed, until this succeeds on a "
                                + "later restart)",
                        e);
            }
        };
    }
}
