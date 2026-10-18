package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;

import java.util.Map;
import java.util.Set;

/** Pure, stateless — no Spring, no persistence. Portable as-is from tnt-svc. */
public final class TenantStateMachine {

    private static final Map<TenantStatus, Set<TenantStatus>> ALLOWED_TRANSITIONS = Map.of(
        TenantStatus.PROVISIONING, Set.of(TenantStatus.ACTIVE, TenantStatus.CANCELLED),
        TenantStatus.ACTIVE, Set.of(TenantStatus.SUSPENDED, TenantStatus.CANCELLED),
        TenantStatus.SUSPENDED, Set.of(TenantStatus.ACTIVE, TenantStatus.CANCELLED),
        TenantStatus.CANCELLED, Set.of(TenantStatus.PURGED),
        TenantStatus.PURGED, Set.of()
    );

    private TenantStateMachine() {
    }

    public static boolean isTransitionAllowed(TenantStatus from, TenantStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(TenantStatus from, TenantStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new InvalidTenantStateTransitionException(from, to);
        }
    }
}
