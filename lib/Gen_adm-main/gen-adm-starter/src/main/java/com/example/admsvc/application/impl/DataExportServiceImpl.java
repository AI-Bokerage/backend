package com.example.admsvc.application.impl;

import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.api.dto.response.SupportTicketResponse;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DataExportServiceImpl implements DataExportService {

    private final DataExportRepository dataExportRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OffboardingJobRepository offboardingJobRepository;
    private final ImpersonationSessionRepository impersonationSessionRepository;
    private final InvitationRepository invitationRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final GenAdmProperties properties;
    private final ObjectMapper objectMapper;

    public DataExportServiceImpl(DataExportRepository dataExportRepository,
                                  RoleRepository roleRepository,
                                  UserRoleAssignmentRepository userRoleAssignmentRepository,
                                  OffboardingJobRepository offboardingJobRepository,
                                  ImpersonationSessionRepository impersonationSessionRepository,
                                  InvitationRepository invitationRepository,
                                  SupportTicketRepository supportTicketRepository,
                                  GenAdmProperties properties,
                                  ObjectMapper objectMapper) {
        this.dataExportRepository = dataExportRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.offboardingJobRepository = offboardingJobRepository;
        this.impersonationSessionRepository = impersonationSessionRepository;
        this.invitationRepository = invitationRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DataExportEntity request(UUID tenantId, UUID requestedByUserId) {
        List<RoleResponse> roles = roleRepository.findAllByTenantId(tenantId).stream()
                .map(RoleResponse::from).toList();
        List<AssignmentResponse> assignments = userRoleAssignmentRepository.findAllByTenantId(tenantId).stream()
                .map(AssignmentResponse::from).toList();
        List<OffboardingJobResponse> offboardingJobs = offboardingJobRepository.findAllByTenantId(tenantId).stream()
                .map(OffboardingJobResponse::from).toList();
        List<ImpersonationSessionResponse> impersonationSessions =
                impersonationSessionRepository.findAllByTenantId(tenantId).stream()
                        .map(ImpersonationSessionResponse::from).toList();
        List<InvitationResponse> invitations = invitationRepository.findAllByTenantId(tenantId).stream()
                .map(InvitationResponse::from).toList();
        List<SupportTicketResponse> supportTickets = supportTicketRepository.findAllByTenantId(tenantId).stream()
                .map(SupportTicketResponse::from).toList();

        TenantExportSnapshot snapshot = new TenantExportSnapshot(
                tenantId, Instant.now(), roles, assignments, offboardingJobs,
                impersonationSessions, invitations, supportTickets);

        long recordCount = roles.size() + assignments.size() + offboardingJobs.size()
                + impersonationSessions.size() + invitations.size() + supportTickets.size();

        DataExportEntity export = DataExportEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .status(DataExportStatus.COMPLETED)
                .snapshotJson(writeSnapshot(snapshot))
                .recordCount(recordCount)
                .expiresAt(Instant.now().plus(properties.getExportTtlDays(), ChronoUnit.DAYS))
                .build();
        return dataExportRepository.saveAndFlush(export);
    }

    @Override
    @Transactional
    public DataExportEntity getStatus(UUID tenantId, UUID exportId) {
        return expireIfOverdue(findOrThrow(tenantId, exportId));
    }

    @Override
    @Transactional
    public List<DataExportEntity> listExports(UUID tenantId) {
        return dataExportRepository.findAllByTenantId(tenantId).stream()
                .map(this::expireIfOverdue)
                .toList();
    }

    @Override
    @Transactional
    public String download(UUID tenantId, UUID exportId) {
        DataExportEntity export = expireIfOverdue(findOrThrow(tenantId, exportId));
        if (export.getStatus() != DataExportStatus.COMPLETED) {
            throw new GenAdmConflictException("Export is not downloadable: " + exportId);
        }
        return export.getSnapshotJson();
    }

    @Override
    @Transactional
    public DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId) {
        DataExportEntity export = expireIfOverdue(findOrThrow(tenantId, exportId));
        if (export.getStatus() != DataExportStatus.COMPLETED) {
            throw new GenAdmConflictException("Export cannot be revoked: " + exportId);
        }
        export.setStatus(DataExportStatus.REVOKED);
        export.setRevokedAt(Instant.now());
        export.setRevokedByUserId(revokedByUserId);
        export.setSnapshotJson(null);
        return dataExportRepository.saveAndFlush(export);
    }

    private DataExportEntity findOrThrow(UUID tenantId, UUID exportId) {
        return dataExportRepository.findByIdAndTenantId(exportId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Export not found: " + exportId));
    }

    private DataExportEntity expireIfOverdue(DataExportEntity export) {
        if (export.getStatus() == DataExportStatus.COMPLETED && Instant.now().isAfter(export.getExpiresAt())) {
            export.setStatus(DataExportStatus.EXPIRED);
            export.setSnapshotJson(null);
            return dataExportRepository.saveAndFlush(export);
        }
        return export;
    }

    private String writeSnapshot(TenantExportSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tenant export snapshot", e);
        }
    }
}
