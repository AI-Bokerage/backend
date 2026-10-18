package com.company.bsmsvc.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical wire payload for all BSM add-on lifecycle events.
 *
 * <p>Published on {@code cpms.events} exchange with routing keys:
 * <ul>
 *   <li>{@code bsm.addon.activated}</li>
 *   <li>{@code bsm.addon.deactivated}</li>
 * </ul>
 *
 * <p>Published uniformly for every add-on type (feature/quota/service) — the
 * type is carried in {@code addOnType} so each consumer decides for itself
 * whether an event is relevant (e.g. fmm-svc only acts on {@code "feature"}).
 *
 * <p>{@code addOnCode}/{@code addOnType}/{@code quotaMeterCode}/{@code quotaAmount}
 * are nullable: they come from an enrichment lookup against PPM-SVC's add-on
 * catalog, which is best-effort — a lookup failure must never block or roll
 * back a purchase/removal that has already been persisted and charged.
 * {@code quotaMeterCode}/{@code quotaAmount} are only populated when
 * {@code addOnType == "quota"}.
 *
 * <p>{@code version = 1} for future compatibility.
 */
public record AddOnEventPayload(
    int     version,
    String  eventType,
    UUID    tenantId,
    UUID    subscriptionId,
    UUID    subscriptionAddOnId,
    UUID    ppmAddOnId,
    String  addOnCode,
    String  addOnType,
    String  quotaMeterCode,
    Integer quotaAmount,
    Instant effectiveAt
) {
    public static final int CURRENT_VERSION = 1;
}
