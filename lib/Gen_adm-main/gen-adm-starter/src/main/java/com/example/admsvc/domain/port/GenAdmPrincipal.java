package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Implemented by whatever object the host application's
 * {@code Authentication#getPrincipal()} returns (directly, or via a small
 * adapter the host registers) — Gen_ADM never imports a Gen_AUTH class
 * directly, staying loosely coupled to any specific auth library.
 */
public interface GenAdmPrincipal {

    UUID tenantId();

    UUID userId();
}
