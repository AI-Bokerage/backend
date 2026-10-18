package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.stripe")
public record StripeProperties(String secretKey) {}
