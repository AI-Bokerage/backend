package com.company.bsmsvc.domain.model;

/** Default trial length for newly-onboarded tenants on the trial/free plan. */
public record TrialPolicy(int defaultTrialDays) {}
