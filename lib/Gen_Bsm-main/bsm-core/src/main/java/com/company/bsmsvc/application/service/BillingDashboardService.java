package com.company.bsmsvc.application.service;

import java.util.UUID;
import com.company.bsmsvc.domain.model.BillingSummary;

/**
 * Aggregates a tenant's billing summary for dashboard display. Experimental tier — the reference
 * implementation lives in {@code bsm-svc}, not {@code bsm-core}, and queries via raw SQL rather
 * than through a dedicated repository port; see {@code TECHNICAL_DEBT_REGISTER.md}.
 */
public interface BillingDashboardService {
    BillingSummary getSummary(UUID tenantId, int recentLimit);
}
