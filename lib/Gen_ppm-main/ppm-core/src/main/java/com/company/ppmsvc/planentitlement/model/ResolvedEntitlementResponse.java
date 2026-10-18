package com.company.ppmsvc.planentitlement.model;

import com.company.ppmsvc.entitlement.model.EntitlementType;

/**
 * A single resolved entitlement for a plan.
 *
 * <p>Carries the entitlement's stable code, its catalog display name, its
 * type, and the concrete value assigned to the plan. {@code name} lets
 * callers (e.g. REG-SVC's signup pricing page) render a human-readable
 * feature bullet for any entitlement — including ones with no bespoke
 * formatting logic — without needing their own hardcoded code→label table.
 * Callers interpret {@code value} according to {@code type}:
 * <ul>
 *   <li>{@code BOOLEAN} — {@code "true"} or {@code "false"}</li>
 *   <li>{@code QUOTA}   — a numeric string, e.g. {@code "25"}</li>
 *   <li>{@code RATE_LIMIT} — a numeric string, e.g. {@code "500"}</li>
 * </ul>
 */
public record ResolvedEntitlementResponse(

    String code,
    String name,
    EntitlementType type,
    String value
) {}
