package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Distribution-channel tag on a {@link Promotion}.
 *
 * <p>Purely informational in Phase 2 — no routing logic or per-source
 * behavior. Distinguishes the distribution channel (e.g. "this 10%-off
 * promotion came from an influencer collaboration"). {@code EMPLOYEE},
 * {@code EVENT}, and {@code AFFILIATE} are intentionally omitted from this
 * lean vocabulary; additive later without breaking compatibility.
 *
 * <p>Persisted explicitly, never {@code null} — the DB column is {@code NOT
 * NULL DEFAULT 'normal'} and the domain builder defaults to {@link #NORMAL}.
 */
@Getter
@RequiredArgsConstructor
public enum PromotionSource {

    NORMAL     ("normal"),
    PARTNER    ("partner"),
    INFLUENCER ("influencer"),
    REFERRAL   ("referral");

    @JsonValue
    private final String value;

    @JsonCreator
    public static PromotionSource fromValue(String value) {
        for (PromotionSource s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown PromotionSource value: " + value);
    }
}
