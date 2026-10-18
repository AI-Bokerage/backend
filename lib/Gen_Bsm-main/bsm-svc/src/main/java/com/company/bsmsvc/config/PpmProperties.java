package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services.ppm-svc")
public record PpmProperties(
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs
) {}
