package com.example.modauth.service;

import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.InvitationStatus;
import com.example.modauth.domain.Role;
import com.example.modauth.entity.InvitationEntity;
import com.example.modauth.repository.InvitationJpaRepository;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TeamJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Resend, or cancel the invitation" — in particular, that a Super Admin
 * can manage a brokerage-owner invitation even though its inviter is a
 * service account with the brokerage's own tenantId, not the Super Admin's.
 */
@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock private InvitationJpaRepository invitationRepo;
    @Mock private ModAuthUserRoleJpaRepository roleRepo;
    @Mock private ModAuthUserLookupRepository userLookupRepo;
    @Mock private TeamJpaRepository teamRepo;
    @Mock private RegisterService registerService;
    @Mock private RoleResolver roleResolver;

    private InvitationServiceImpl service;

    private final UUID callerTenantId = UUID.randomUUID();
    private final UUID brokerageTenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvitationServiceImpl(invitationRepo, roleRepo, userLookupRepo, teamRepo, registerService, roleResolver);
    }

    private AuthenticatedUser caller(UUID tenantId) {
        return new AuthenticatedUser(UUID.randomUUID(), tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
    }

    private InvitationEntity pendingInvitation(UUID tenantId) {
        return InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).inviterUserId(UUID.randomUUID())
                .name("Jamie").email("jamie@example.com").role(Role.TENANT_ADMIN)
                .status(InvitationStatus.PENDING).tokenHash("hash").expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void sameTenantCanResendWithoutNeedingSuperAdmin() {
        AuthenticatedUser admin = caller(brokerageTenantId);
        InvitationEntity invitation = pendingInvitation(brokerageTenantId);
        when(invitationRepo.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        service.resend(admin, invitation.getId());

        verify(invitationRepo).save(invitation);
        verify(roleResolver, never()).resolve(admin);
    }

    @Test
    void differentTenantNonSuperAdminCannotResend() {
        AuthenticatedUser caller = caller(callerTenantId);
        InvitationEntity invitation = pendingInvitation(brokerageTenantId);
        when(invitationRepo.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(roleResolver.resolve(caller)).thenReturn(Role.TEAM_LEAD);

        assertThatThrownBy(() -> service.resend(caller, invitation.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your brokerage's invitation");
    }

    @Test
    void superAdminFromADifferentTenantCanResendABrokerageOwnerInvite() {
        AuthenticatedUser superAdmin = caller(callerTenantId);
        InvitationEntity invitation = pendingInvitation(brokerageTenantId);
        when(invitationRepo.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(roleResolver.resolve(superAdmin)).thenReturn(Role.SUPER_ADMIN);

        service.resend(superAdmin, invitation.getId());

        verify(invitationRepo).save(invitation);
    }

    @Test
    void superAdminCanCancelAPendingBrokerageOwnerInvite() {
        AuthenticatedUser superAdmin = caller(callerTenantId);
        InvitationEntity invitation = pendingInvitation(brokerageTenantId);
        when(invitationRepo.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(roleResolver.resolve(superAdmin)).thenReturn(Role.SUPER_ADMIN);

        service.cancel(superAdmin, invitation.getId());

        verify(invitationRepo).delete(invitation);
    }

    @Test
    void cannotCancelAnAlreadyAcceptedInvitation() {
        AuthenticatedUser admin = caller(brokerageTenantId);
        InvitationEntity invitation = pendingInvitation(brokerageTenantId);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepo.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.cancel(admin, invitation.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already accepted");

        verify(invitationRepo, never()).delete(invitation);
    }

    @Test
    void cancellingAMissingInvitationIs404() {
        AuthenticatedUser admin = caller(brokerageTenantId);
        UUID missingId = UUID.randomUUID();
        when(invitationRepo.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(admin, missingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invitation not found");
    }
}
