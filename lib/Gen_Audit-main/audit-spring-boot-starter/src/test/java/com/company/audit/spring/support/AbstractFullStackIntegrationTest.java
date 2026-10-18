package com.company.audit.spring.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for tests that need real Postgres, Mongo, and RabbitMQ instances via
 * Testcontainers — see {@link AbstractPostgresIntegrationTest} for why the "singleton container"
 * (static initializer, never explicitly stopped) pattern is required here rather than
 * {@code @Testcontainers}/{@code @Container}.
 */
public abstract class AbstractFullStackIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
    protected static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        MONGO.start();
        RABBIT.start();
    }

    /**
     * Registers all three containers' connection details as Spring properties.
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
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
}
