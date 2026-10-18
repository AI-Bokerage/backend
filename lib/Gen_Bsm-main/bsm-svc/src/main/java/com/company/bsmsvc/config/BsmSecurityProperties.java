package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BSM-SVC security configuration properties.
 *
 * <pre>
 * bsm:
 *   internal-secret: ${INTERNAL_SERVICE_SECRET:change-me-in-production}
 * </pre>
 *
 * {@code internalSecret} is the pre-shared secret that callers must supply in the
 * {@code X-Internal-Secret} header when calling {@code /internal/**} endpoints.
 */
@ConfigurationProperties(prefix = "bsm")
public record BsmSecurityProperties(String internalSecret) {}
