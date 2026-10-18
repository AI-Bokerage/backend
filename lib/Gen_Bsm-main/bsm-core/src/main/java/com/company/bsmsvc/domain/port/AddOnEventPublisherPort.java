package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.SubscriptionAddOn;

/** Publishes add-on lifecycle business events. */
public interface AddOnEventPublisherPort {

    void publishActivated(SubscriptionAddOn addOn);

    void publishDeactivated(SubscriptionAddOn addOn);
}
