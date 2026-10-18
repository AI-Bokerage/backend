package com.company.bsmsvc.domain.model;

import java.util.List;

/**
 * Domain value object representing the impact analysis of a prospective downgrade.
 * Not persisted — derived at request time by the downgrade preflight engine.
 */
public record DowngradeImpact(
    DowngradeImpactDetails details,
    List<DowngradeWarning> warnings
) {
    public boolean isOverLimit() {
        return details.usersOverLimit() > 0
            || details.projectsOverLimit() > 0
            || details.storageOverLimitBytes() > 0
            || !details.featuresLost().isEmpty();
    }
}
