package com.company.bsmsvc.infrastructure.client.ppm;

import java.util.UUID;

/**
 * BSM-side representation of PPM's AddOnResponse.
 *
 * <p>Returned by {@code GET /api/v1/ppm/add-ons/{id}}. Used by
 * {@link PpmAddOnCatalogClient} to enrich add-on lifecycle events with the
 * add-on's human-readable {@code code} and {@code type} — bsm-svc has no
 * {@code AddOnType} enum of its own, so {@code type} is carried as PPM's raw
 * wire value (e.g. {@code "feature"}, {@code "quota"}, {@code "service"}).
 *
 * <p>{@code quotaMeterCode}/{@code quotaAmount} are only set when
 * {@code type == "quota"} — carried through as-is for usg-svc's consumer.
 */
public record PpmAddOnCatalogResult(
    UUID    id,
    String  code,
    String  name,
    String  type,
    boolean active,
    String  quotaMeterCode,
    Integer quotaAmount
) {}
