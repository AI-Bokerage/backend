package com.company.bsmsvc.domain.model.payment;

public record DetachPaymentMethodCommand(String externalCustomerId, String paymentMethodId) {}
