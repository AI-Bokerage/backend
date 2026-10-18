package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.CheckoutResult;
import com.company.bsmsvc.domain.model.InitiateCheckoutCommand;

/**
 * Orchestrates the PPM-backed checkout flow (Phase C1).
 *
 * <p>Flow:
 * <ol>
 *   <li>Resolve price from PPM using ppmPlanId + region + currency + cycle.</li>
 *   <li>Validate promo code from PPM (if provided).</li>
 *   <li>Create subscription via existing BSM subscription service.</li>
 *   <li>Generate invoice with PPM-resolved amount + optional discount line item.</li>
 *   <li>Create payment provider checkout session.</li>
 * </ol>
 *
 * <p>No existing BSM endpoint is modified. This is an additive path only.
 */
public interface PpmCheckoutService {

    CheckoutResult initiateCheckout(InitiateCheckoutCommand command);
}
