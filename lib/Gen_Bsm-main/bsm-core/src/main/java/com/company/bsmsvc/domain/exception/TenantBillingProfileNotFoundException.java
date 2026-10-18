package com.company.bsmsvc.domain.exception;

public class TenantBillingProfileNotFoundException extends RuntimeException {

    public TenantBillingProfileNotFoundException(String message) {
        super(message);
    }
}
