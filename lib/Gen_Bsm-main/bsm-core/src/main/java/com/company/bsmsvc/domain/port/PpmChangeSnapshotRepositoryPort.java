package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PpmChangeSnapshot;
import java.util.List;
import java.util.UUID;

/**
 * Persistence/query port for PPM plan-change snapshots (a record of a subscription's catalog
 * reference state at the time of a plan change, used for diagnostics/integrity checks).
 * Implementations must be thread-safe/stateless.
 */
public interface PpmChangeSnapshotRepositoryPort {

    PpmChangeSnapshot save(PpmChangeSnapshot snapshot);

    List<PpmChangeSnapshot> findBySubscriptionId(UUID subscriptionId);
}
