package com.example.authsvc.domain.model;

public record MfaEnrollment(
        String otpauthUri,
        String base32Secret
) {}
