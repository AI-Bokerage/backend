package com.company.bsmsvc.domain.model;

/**
 * Combined user-count result returned by a single ADM-SVC fetch.
 * Replaces the two-method call pattern that produced two HTTP round-trips per operation.
 */
public record UserUsageCounts(int activeInternalUsers, int activeClientUsers) {

    public int totalActive() {
        return activeInternalUsers + activeClientUsers;
    }
}
