package com.company.bsmsvc.infrastructure.client.ppm;

/**
 * Generic envelope for PPM-SVC API responses.
 *
 * <p>All PPM catalog endpoints return {@code { success, message, data }}.
 * This record is used only for JSON deserialization inside the PPM HTTP clients
 * and is never exposed outside the {@code infrastructure.client.ppm} package.
 */
public record PpmApiEnvelope<T>(
    boolean success,
    String  message,
    T       data
) {}
