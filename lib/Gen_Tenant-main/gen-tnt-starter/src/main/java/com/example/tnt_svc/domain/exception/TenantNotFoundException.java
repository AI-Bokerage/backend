package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(UUID id) {
        super("Tenant not found: " + id);
    }

    public TenantNotFoundException(String slug) {
        super("Tenant not found for slug: " + slug);
    }
}
