package com.company.ppmsvc.addonprice.usecase;

import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.common.BillingCycle;
import java.util.UUID;

/**
 * Application service for add-on price resolution.
 *
 * <p>Exposes the single operation needed by BSM C4: resolving the currently-effective,
 * active price for a PPM add-on given a pricing context (region, currency, cycle).
 */
public interface AddOnPriceApplicationService {

    /**
     * Resolves the most recently effective active price for the given add-on.
     *
     * @param addOnId  UUID of the add-on whose price is to be resolved
     * @param region   market / geographic region (e.g. {@code "INDIA"})
     * @param currency ISO 4217 currency code (e.g. {@code "INR"})
     * @param cycle    billing cycle to look up
     * @return the resolved price
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException when no
     *         active price exists for the given combination
     */
    AddOnPrice resolveActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle);
}
