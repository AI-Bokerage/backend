package com.company.ppmsvc.campaign.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lifecycle label for a {@link Campaign} — for management/reporting only.
 * Carries no runtime evaluation logic; it does not gate promotion quoting.
 */
@Getter
@RequiredArgsConstructor
public enum CampaignStatus {

    DRAFT     ("draft"),
    ACTIVE    ("active"),
    COMPLETED ("completed"),
    ARCHIVED  ("archived");

    @JsonValue
    private final String value;

    @JsonCreator
    public static CampaignStatus fromValue(String value) {
        for (CampaignStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown CampaignStatus value: " + value);
    }
}
