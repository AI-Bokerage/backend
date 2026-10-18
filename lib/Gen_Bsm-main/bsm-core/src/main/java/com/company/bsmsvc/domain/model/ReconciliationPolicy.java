package com.company.bsmsvc.domain.model;

/**
 * Threshold for flagging a stale PENDING payment as due for reconciliation.
 * Host-configured, injected as a plain value object at wiring time.
 */
public record ReconciliationPolicy(int thresholdSeconds) {}
