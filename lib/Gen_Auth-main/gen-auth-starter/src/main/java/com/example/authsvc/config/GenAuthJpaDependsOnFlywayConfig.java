// gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthJpaDependsOnFlywayConfig.java
package com.example.authsvc.config;

import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Forces the host application's JPA {@code EntityManagerFactory} bean to depend on
 * {@link GenAuthFlywayInitializer}, so Gen_AUTH's migrations are guaranteed to have
 * already run by the time Hibernate's {@code ddl-auto: validate} check executes.
 * This is the same mechanism (a public Spring Boot API) Spring Boot's own built-in
 * Flyway/Liquibase autoconfiguration uses internally for the identical problem.
 */
@Configuration
public class GenAuthJpaDependsOnFlywayConfig extends EntityManagerFactoryDependsOnPostProcessor {

    public GenAuthJpaDependsOnFlywayConfig() {
        super("genAuthFlywayInitializer");
    }
}
