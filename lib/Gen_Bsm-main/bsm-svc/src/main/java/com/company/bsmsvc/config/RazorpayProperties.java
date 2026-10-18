package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.razorpay")
public record RazorpayProperties(String keyId, String keySecret) {}
