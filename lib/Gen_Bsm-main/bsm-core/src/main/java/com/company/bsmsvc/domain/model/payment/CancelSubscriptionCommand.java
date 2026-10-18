package com.company.bsmsvc.domain.model.payment;

public record CancelSubscriptionCommand(String externalSubscriptionId, boolean immediately) {}
