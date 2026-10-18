package com.company.audit.spring.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Base class for tests that need a real Mongo instance via Testcontainers.
 *
 * <p>Uses the same "singleton container" pattern as {@link AbstractPostgresIntegrationTest}, for
 * the same reason: a {@code @Testcontainers}/{@code @Container}-managed field inherited from an
 * abstract base gets its lifecycle re-managed per subclass, stopping the container after the
 * first test class finishes. Starting it once in a static initializer and never explicitly
 * stopping it (Ryuk cleans up at JVM exit) is what actually shares one instance across classes.
 */
public abstract class AbstractMongoIntegrationTest {

    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        MONGO.start();
    }

    /**
     * Registers the running container's connection URI as a Spring Mongo property.
     *
     * @param registry the registry to add dynamic properties to
     */
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // Boot 4 drives the actual MongoClient bean from spring.mongodb.uri, not
        // spring.data.mongodb.uri (which still exists in config metadata but no longer governs
        // the connection) — confirmed by reading spring-boot-mongodb's own metadata after a
        // MongoTimeoutException against the driver's hardcoded localhost:27017 default revealed
        // the property wasn't being picked up.
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}
