package com.example.tnt_svc.domain.exception;

public class DuplicateSlugException extends RuntimeException {
    public DuplicateSlugException(String slug) {
        super("Tenant slug already in use: " + slug);
    }
}
