package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.Subscription;
import java.util.UUID;

/**
 * Publishes subscription lifecycle business events. The host decides how
 * these are delivered downstream (outbox, message broker, etc.) — the
 * library only signals that the lifecycle transition occurred.
 */
public interface SubscriptionEventPublisherPort {

    void publishCreated(Subscription subscription);

    void publishChanged(Subscription subscription, String oldPlanCode, String reason);

    void publishCanceled(Subscription subscription);

    void publishExpired(Subscription subscription);

    void publishRenewed(Subscription subscription);

    void publishUpgraded(Subscription subscription, UUID fromPlanVersionId);
}
