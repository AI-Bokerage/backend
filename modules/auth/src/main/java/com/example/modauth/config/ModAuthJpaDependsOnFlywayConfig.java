package com.example.modauth.config;

import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Same mechanism as gen-auth-starter's own {@code GenAuthJpaDependsOnFlywayConfig},
 * pointed at this module's {@link ModAuthFlywayInitializer} bean — both
 * post-processors stack, so Hibernate's {@code ddl-auto: validate} only runs
 * once both migration sets have applied.
 */
@Configuration
public class ModAuthJpaDependsOnFlywayConfig extends EntityManagerFactoryDependsOnPostProcessor {

    public ModAuthJpaDependsOnFlywayConfig() {
        super("modAuthFlywayInitializer");
    }
}
