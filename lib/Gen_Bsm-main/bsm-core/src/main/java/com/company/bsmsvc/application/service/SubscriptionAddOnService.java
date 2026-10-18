package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.AddOnPurchaseResult;
import com.company.bsmsvc.domain.model.PurchaseAddOnCommand;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import java.util.List;
import java.util.UUID;

/**
 * Purchases, lists, and removes subscription add-ons. Not auto-configured by the starter —
 * requires manual wiring, see {@code docs/examples/add-on-purchase.md} and
 * {@code TECHNICAL_DEBT_REGISTER.md}.
 */
public interface SubscriptionAddOnService {

    AddOnPurchaseResult purchaseAddOn(UUID subscriptionId, PurchaseAddOnCommand command);

    List<SubscriptionAddOn> listAddOns(UUID subscriptionId, UUID tenantId);

    void removeAddOn(UUID subscriptionId, UUID ppmAddOnId, UUID tenantId, String performedBy);
}
