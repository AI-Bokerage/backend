package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.auth-svc")
public record AuthProperties(
    String baseUrl,
    String internalSecret,
    int connectTimeoutMs,
    int readTimeoutMs
) {}
