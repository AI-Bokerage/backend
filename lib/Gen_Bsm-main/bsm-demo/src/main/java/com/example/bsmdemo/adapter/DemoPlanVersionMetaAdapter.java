package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Hardcoded "Starter" plan version — stands in for a real PPM-SVC lookup. */
@Component
public class DemoPlanVersionMetaAdapter implements PlanVersionMetaPort {

    @Override
    public PpmVersionMetaResult getVersionMeta(UUID versionId) {
        return new PpmVersionMetaResult(
            versionId,
            UUID.nameUUIDFromBytes("demo-starter-plan".getBytes()),
            "STARTER",
            1,
            true,
            true,
            LocalDate.of(2024, 1, 1),
            null,
            "starter"
        );
    }
}
