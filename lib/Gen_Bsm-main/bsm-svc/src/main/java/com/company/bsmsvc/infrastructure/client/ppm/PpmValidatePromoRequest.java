package com.company.bsmsvc.infrastructure.client.ppm;

import java.util.UUID;

/**
 * Outgoing request body for {@code POST /api/v1/ppm/promo-codes/validate}.
 */
public record PpmValidatePromoRequest(
    String code,
    UUID   planId
) {}
