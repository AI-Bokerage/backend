package com.company.audit.spring.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal Spring Boot application used only to boot a test context that exercises this
 * starter's auto-configurations against a real Testcontainers Postgres instance.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
}
