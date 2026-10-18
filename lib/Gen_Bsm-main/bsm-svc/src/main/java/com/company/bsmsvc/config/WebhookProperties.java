package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.webhook")
public record WebhookProperties(String stripeSecret, String razorpaySecret) {}
