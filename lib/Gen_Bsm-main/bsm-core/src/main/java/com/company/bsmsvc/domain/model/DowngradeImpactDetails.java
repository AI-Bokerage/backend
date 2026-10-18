package com.company.bsmsvc.domain.model;

import java.util.List;

/**
 * Numerical details component of a DowngradeImpact analysis.
 */
public record DowngradeImpactDetails(
    int usersOverLimit,
    int projectsOverLimit,
    long storageOverLimitBytes,
    List<String> featuresLost
) {
}
