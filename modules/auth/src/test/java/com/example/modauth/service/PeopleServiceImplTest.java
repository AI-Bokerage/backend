package com.example.modauth.service;

import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Implements the "People" flow diagram: list, and "Removing someone" (deactivate + revoke sessions). */
@ExtendWith(MockitoExtension.class)
class PeopleServiceImplTest {

    @Mock private ModAuthUserRoleJpaRepository roleRepo;
    @Mock private ModAuthUserLookupRepository userLookupRepo;
    @Mock private AuthRefreshTokenJpaRepository refreshTokenRepo;
    @Mock private RoleResolver roleResolver;
    @Mock private PersonMapper personMapper;
    @Mock private CrmClient crmClient;

    private PeopleServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new PeopleServiceImpl(roleRepo, userLookupRepo, refreshTokenRepo, roleResolver, personMapper, crmClient);
        admin = new AuthenticatedUser(UUID.randomUUID(), tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
    }

    private ModAuthUserRoleEntity roleRow(UUID userId, UUID tenant, Role role) {
        return ModAuthUserRoleEntity.builder().userId(userId).tenantId(tenant).role(role).acceptedTermsVersion(1).build();
    }

    @Test
    void listExcludesTenantAdminsAndMapsEveryoneElse() {
        UUID leadId = UUID.randomUUID();
        UUID selfAdminId = admin.getUserId();
        when(roleRepo.findByTenantId(tenantId)).thenReturn(List.of(
                roleRow(leadId, tenantId, Role.TEAM_LEAD),
                roleRow(selfAdminId, tenantId, Role.TENANT_ADMIN)
        ));
        when(personMapper.toResponse(any())).thenAnswer(inv -> {
            ModAuthUserRoleEntity r = inv.getArgument(0);
            return new PersonResponse(r.getUserId(), null, null, r.getRole(), null, null, true);
        });

        List<PersonResponse> people = service.list(admin);

        assertThat(people).hasSize(1);
        assertThat(people.get(0).role()).isEqualTo(Role.TEAM_LEAD);
    }

    @Test
    void deactivateFlipsActiveFlagAndRevokesAllSessions() {
        UUID brokerId = UUID.randomUUID();
        ModAuthUserRoleEntity roleRow = roleRow(brokerId, tenantId, Role.BROKER);
        AuthUserEntity authUser = AuthUserEntity.builder().id(brokerId).tenantId(tenantId).active(true).build();
        when(roleRepo.findById(brokerId)).thenReturn(Optional.of(roleRow));
        when(userLookupRepo.findById(brokerId)).thenReturn(Optional.of(authUser));

        service.deactivate(admin, brokerId);

        assertThat(authUser.isActive()).isFalse();
        verify(userLookupRepo).save(authUser);
        verify(refreshTokenRepo, times(1)).revokeAllByUserId(eq(brokerId), any(Instant.class));
    }

    @Test
    void cannotDeactivateSomeoneFromAnotherTenant() {
        UUID otherTenantUserId = UUID.randomUUID();
        when(roleRepo.findById(otherTenantUserId)).thenReturn(Optional.of(roleRow(otherTenantUserId, UUID.randomUUID(), Role.BROKER)));

        assertThatThrownBy(() -> service.deactivate(admin, otherTenantUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your brokerage's person");

        verify(refreshTokenRepo, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void cannotDeactivateATenantAdminThroughThisEndpoint() {
        UUID otherAdminId = UUID.randomUUID();
        when(roleRepo.findById(otherAdminId)).thenReturn(Optional.of(roleRow(otherAdminId, tenantId, Role.TENANT_ADMIN)));

        assertThatThrownBy(() -> service.deactivate(admin, otherAdminId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot deactivate a Tenant Admin");

        verify(userLookupRepo, never()).save(any());
        verify(refreshTokenRepo, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void deactivateBlockedWhileClientsAreStillAssigned() {
        UUID brokerId = UUID.randomUUID();
        when(roleRepo.findById(brokerId)).thenReturn(Optional.of(roleRow(brokerId, tenantId, Role.BROKER)));
        when(crmClient.activeAssignedLeadCount(tenantId, brokerId)).thenReturn(14);

        assertThatThrownBy(() -> service.deactivate(admin, brokerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reassign their 14 clients first");

        verify(userLookupRepo, never()).save(any());
        verify(refreshTokenRepo, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void deactivateNonExistentPersonIs404() {
        UUID missing = UUID.randomUUID();
        when(roleRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(admin, missing))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Person not found");
    }
}
