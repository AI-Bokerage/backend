package com.company.bsmsvc.domain.model;

/**
 * Dunning retry/suspend/cancel schedule. Values are host-configured
 * (application.yml) and injected by the host at wiring time — the library
 * only depends on this plain value object, not the host's configuration
 * binding mechanism.
 */
public record DunningPolicy(
    int day1RetryAfterHours,
    int day3RetryAfterHours,
    int day7RetryAfterHours,
    int suspendAfterDays,
    int cancelAfterDays
) {}
