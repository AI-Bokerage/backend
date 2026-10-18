package com.example.tnt_svc.domain.exception;

import com.example.tnt_svc.domain.TenantStatus;

public class InvalidTenantStateTransitionException extends RuntimeException {

    public InvalidTenantStateTransitionException(TenantStatus from, TenantStatus to) {
        super("Cannot transition tenant from " + from + " to " + to);
    }
}
