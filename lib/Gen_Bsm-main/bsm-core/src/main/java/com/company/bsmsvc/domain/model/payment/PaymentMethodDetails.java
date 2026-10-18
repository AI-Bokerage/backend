package com.company.bsmsvc.domain.model.payment;

public record PaymentMethodDetails(
    String externalPaymentMethodId,
    String type,
    String brand,
    String lastFour,
    Integer expMonth,
    Integer expYear
) {}
