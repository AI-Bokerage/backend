package com.example.modauth.service;

import java.util.UUID;

/** Calls out to modules/crm (same Node app, internal-secret-protected). */
public interface CrmClient {

    /** "Do they still have clients assigned?" (SOW 2.11) — every lead/client still on this broker's book. */
    int activeAssignedLeadCount(UUID tenantId, UUID userId);

    /** Flow 0 "Tenant Admin → complete the brokerage profile, required before anything else". */
    boolean isBrokerageProfileComplete(UUID tenantId);
}
