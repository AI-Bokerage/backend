package com.company.audit.spring.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need both a real Postgres instance and a real Mongo instance via
 * Testcontainers — see {@link AbstractPostgresIntegrationTest} and
 * {@link AbstractMongoIntegrationTest} for why the "singleton container" (static initializer,
 * never explicitly stopped) pattern is required here rather than {@code @Testcontainers}/
 * {@code @Container}.
 */
public abstract class AbstractPostgresAndMongoIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        POSTGRES.start();
        MONGO.start();
    }

    /**
     * Registers both containers' connection details as Spring properties.
     *
     * @param registry the registry to add dynamic properties to
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Boot 4 drives the actual MongoClient bean from spring.mongodb.uri, not
        // spring.data.mongodb.uri — see AbstractMongoIntegrationTest for how this was found.
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}
