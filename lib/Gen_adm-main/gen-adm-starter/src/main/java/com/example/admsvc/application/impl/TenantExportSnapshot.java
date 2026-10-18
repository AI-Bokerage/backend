package com.example.admsvc.application.impl;

import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.api.dto.response.SupportTicketResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full JSON document persisted into {@code DataExportEntity.snapshotJson}.
 * Reuses each feature's existing REST response shape rather than inventing
 * a new one — this is the same data those features' own GET endpoints
 * already return, just assembled into a single tenant-wide document.
 */
public record TenantExportSnapshot(
        UUID tenantId,
        Instant generatedAt,
        List<RoleResponse> roles,
        List<AssignmentResponse> userRoleAssignments,
        List<OffboardingJobResponse> offboardingJobs,
        List<ImpersonationSessionResponse> impersonationSessions,
        List<InvitationResponse> invitations,
        List<SupportTicketResponse> supportTickets) {
}
