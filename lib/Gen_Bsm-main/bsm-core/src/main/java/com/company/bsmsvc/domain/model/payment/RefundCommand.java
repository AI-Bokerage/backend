package com.company.bsmsvc.domain.model.payment;

public record RefundCommand(String externalChargeId, long amountMinor, String reason) {}
