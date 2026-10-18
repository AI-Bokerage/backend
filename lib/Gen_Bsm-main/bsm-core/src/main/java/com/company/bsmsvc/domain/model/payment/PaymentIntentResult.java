package com.company.bsmsvc.domain.model.payment;

public record PaymentIntentResult(String paymentIntentId, String clientSecret, String status) {}
