package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.usg-svc")
public record UsgProperties(
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs
) {}
