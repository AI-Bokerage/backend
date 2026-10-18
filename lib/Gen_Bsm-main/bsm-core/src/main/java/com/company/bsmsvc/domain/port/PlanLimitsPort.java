package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmPlanLimitsResult;
import java.util.UUID;

/** Resolves plan version entitlement limits from the plan catalog. */
public interface PlanLimitsPort {
    PpmPlanLimitsResult getLimits(UUID ppmPlanVersionId);
}
