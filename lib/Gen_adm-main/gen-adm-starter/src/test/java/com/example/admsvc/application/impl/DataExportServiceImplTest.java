package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExportServiceImplTest {

    @Mock private DataExportRepository dataExportRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private OffboardingJobRepository offboardingJobRepository;
    @Mock private ImpersonationSessionRepository impersonationSessionRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private SupportTicketRepository supportTicketRepository;

    private GenAdmProperties properties;
    private ObjectMapper objectMapper;
    private DataExportServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new GenAdmProperties();
        properties.setExportTtlDays(7);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new DataExportServiceImpl(dataExportRepository, roleRepository, userRoleAssignmentRepository,
                offboardingJobRepository, impersonationSessionRepository, invitationRepository,
                supportTicketRepository, properties, objectMapper);
        lenient().when(dataExportRepository.saveAndFlush(any())).thenAnswer(inv -> {
            DataExportEntity export = inv.getArgument(0);
            if (export.getId() == null) {
                export.setId(UUID.randomUUID());
            }
            return export;
        });
    }

    private DataExportEntity export(UUID tenantId, DataExportStatus status, Instant expiresAt) {
        return DataExportEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .status(status)
                .snapshotJson("{}")
                .recordCount(0L)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void requestAssemblesAllSixSectionsAndSumsRecordCount() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        when(roleRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("owner").build()));
        when(userRoleAssignmentRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                UserRoleAssignmentEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .userId(UUID.randomUUID()).roleId(UUID.randomUUID()).build()));
        when(offboardingJobRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                OffboardingJobEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(OffboardingJobStatus.COMPLETED).build()));
        when(impersonationSessionRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                ImpersonationSessionEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(ImpersonationSessionStatus.ENDED).build()));
        when(invitationRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                InvitationEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(InvitationStatus.PENDING).build()));
        when(supportTicketRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                SupportTicketEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .type(SupportTicketType.GENERAL).priority(SupportTicketPriority.LOW)
                        .status(SupportTicketStatus.OPEN).build()));

        DataExportEntity result = service.request(tenantId, requestedBy);

        assertThat(result.getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(result.getRecordCount()).isEqualTo(6L);
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));

        TenantExportSnapshot snapshot = objectMapper.readValue(result.getSnapshotJson(), TenantExportSnapshot.class);
        assertThat(snapshot.tenantId()).isEqualTo(tenantId);
        assertThat(snapshot.roles()).hasSize(1);
        assertThat(snapshot.userRoleAssignments()).hasSize(1);
        assertThat(snapshot.offboardingJobs()).hasSize(1);
        assertThat(snapshot.impersonationSessions()).hasSize(1);
        assertThat(snapshot.invitations()).hasSize(1);
        assertThat(snapshot.supportTickets()).hasSize(1);
    }

    @Test
    void listExportsReturnsEveryExportForTheTenant() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity a = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findAllByTenantId(tenantId)).thenReturn(List.of(a));

        assertThat(service.listExports(tenantId)).containsExactly(a);
    }

    @Test
    void downloadReturnsSnapshotWhileCompleted() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThat(service.download(tenantId, exp.getId())).isEqualTo("{}");
    }

    @Test
    void downloadThrowsConflictWhenRevoked() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.REVOKED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.download(tenantId, exp.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void downloadFlipsToExpiredAndClearsSnapshotOncePastExpiry() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().minus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.download(tenantId, exp.getId()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(exp.getStatus()).isEqualTo(DataExportStatus.EXPIRED);
        assertThat(exp.getSnapshotJson()).isNull();
    }

    @Test
    void revokeSucceedsFromCompleted() {
        UUID tenantId = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        DataExportEntity revoked = service.revoke(tenantId, exp.getId(), revokedBy);

        assertThat(revoked.getStatus()).isEqualTo(DataExportStatus.REVOKED);
        assertThat(revoked.getRevokedByUserId()).isEqualTo(revokedBy);
        assertThat(revoked.getSnapshotJson()).isNull();
    }

    @Test
    void revokeThrowsConflictWhenAlreadyRevoked() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.REVOKED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.revoke(tenantId, exp.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundForACrossTenantLookup() {
        UUID exportId = UUID.randomUUID();
        when(dataExportRepository.findByIdAndTenantId(eq(exportId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(UUID.randomUUID(), exportId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
