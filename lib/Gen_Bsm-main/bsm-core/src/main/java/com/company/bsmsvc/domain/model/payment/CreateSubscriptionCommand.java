package com.company.bsmsvc.domain.model.payment;

import java.time.Instant;
import java.util.Map;

public record CreateSubscriptionCommand(
    String externalCustomerId,
    String externalPriceId,
    Map<String, String> metadata,
    String defaultPaymentMethodId,
    /** Unix epoch seconds at which the trial ends; null means no trial (charge immediately). */
    Instant trialEnd
) {}
