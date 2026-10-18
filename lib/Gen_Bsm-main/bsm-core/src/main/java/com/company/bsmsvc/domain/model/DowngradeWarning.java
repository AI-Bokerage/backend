package com.company.bsmsvc.domain.model;

/**
 * Informational warning generated during downgrade preflight analysis.
 */
public record DowngradeWarning(
    String code,
    String message
) {
}
