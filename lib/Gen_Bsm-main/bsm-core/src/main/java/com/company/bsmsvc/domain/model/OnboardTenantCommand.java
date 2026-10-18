package com.company.bsmsvc.domain.model;

import java.util.UUID;

/** Input to onboarding a newly-created tenant into a BSM subscription. */
public record OnboardTenantCommand(
    UUID tenantId,
    String billingCycle,
    UUID ppmPlanId,
    UUID ppmPlanVersionId,
    String region,
    String sourceChannel
) {}
