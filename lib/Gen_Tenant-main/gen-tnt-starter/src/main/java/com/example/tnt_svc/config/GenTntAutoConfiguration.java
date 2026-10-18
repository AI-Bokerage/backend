// gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntAutoConfiguration.java
package com.example.tnt_svc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.tnt_svc}
 * package tree visible to a host application, regardless of that
 * application's own base package — same idiom as Gen_Auth's
 * {@code GenAuthAutoConfiguration}. Discovered automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@EnableScheduling
@ComponentScan("com.example.tnt_svc")
@EntityScan("com.example.tnt_svc.domain")
@EnableJpaRepositories("com.example.tnt_svc.persistence")
@ConfigurationPropertiesScan("com.example.tnt_svc")
public class GenTntAutoConfiguration {
}
