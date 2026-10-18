package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.reconciliation")
public record ReconciliationProperties(int thresholdSeconds, long intervalMs) {}
