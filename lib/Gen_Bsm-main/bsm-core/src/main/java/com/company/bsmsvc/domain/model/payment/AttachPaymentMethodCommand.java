package com.company.bsmsvc.domain.model.payment;

public record AttachPaymentMethodCommand(String externalCustomerId, String paymentMethodToken) {}
