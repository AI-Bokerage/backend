package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for subscription add-on purchases, including active-add-on lookups
 * used to prevent duplicate purchases. Implementations must be thread-safe/stateless.
 */
public interface SubscriptionAddOnRepositoryPort {

    SubscriptionAddOn save(SubscriptionAddOn addOn);

    List<SubscriptionAddOn> findBySubscriptionId(UUID subscriptionId);

    List<SubscriptionAddOn> findActiveBySubscriptionId(UUID subscriptionId);

    Optional<SubscriptionAddOn> findActiveBySubscriptionIdAndPpmAddOnId(UUID subscriptionId, UUID ppmAddOnId);

    boolean existsActive(UUID subscriptionId, UUID ppmAddOnId);
}
