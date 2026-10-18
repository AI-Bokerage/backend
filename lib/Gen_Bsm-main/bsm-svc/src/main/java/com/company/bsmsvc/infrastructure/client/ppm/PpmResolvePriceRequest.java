package com.company.bsmsvc.infrastructure.client.ppm;

import java.util.UUID;

/**
 * Outgoing request body for {@code POST /api/v1/ppm/prices/resolve}.
 */
public record PpmResolvePriceRequest(
    UUID   planId,
    String region,
    String currency,
    String cycle
) {}
