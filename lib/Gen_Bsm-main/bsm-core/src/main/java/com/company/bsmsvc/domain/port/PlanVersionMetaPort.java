package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import java.util.UUID;

/** Resolves plan version metadata (plan code, tier, active flags) from the plan catalog. */
public interface PlanVersionMetaPort {
    PpmVersionMetaResult getVersionMeta(UUID versionId);
}
