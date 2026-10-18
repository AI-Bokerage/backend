package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmDefaultTrialResult;

/** Resolves the platform's default free/trial plan for tenants with no pre-selected plan. */
public interface DefaultTrialPlanPort {
    PpmDefaultTrialResult getDefaultTrialPlan();
}
