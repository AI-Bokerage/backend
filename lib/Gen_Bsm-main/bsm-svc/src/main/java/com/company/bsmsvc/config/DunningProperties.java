package com.company.bsmsvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dunning")
public record DunningProperties(Policy policy) {

    public record Policy(
        int day1RetryAfterHours,
        int day3RetryAfterHours,
        int day7RetryAfterHours,
        int suspendAfterDays,
        int cancelAfterDays
    ) {}
}
