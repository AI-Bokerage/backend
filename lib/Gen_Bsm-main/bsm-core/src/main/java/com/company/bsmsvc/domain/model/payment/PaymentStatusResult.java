package com.company.bsmsvc.domain.model.payment;

public record PaymentStatusResult(
    String externalId,
    String providerStatus,
    String chargeId,
    boolean succeeded,
    boolean failed
) {}
