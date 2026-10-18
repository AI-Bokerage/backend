package com.example.modauth.service;

import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.CreateTeamRequest;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.dto.TeamResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.entity.TeamEntity;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TeamJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Implements the "Teams" flow diagram: create/list/detail, add/remove members, delete-blocked-while-has-members. */
@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock private TeamJpaRepository teamRepo;
    @Mock private ModAuthUserRoleJpaRepository roleRepo;
    @Mock private RoleResolver roleResolver;
    @Mock private PersonMapper personMapper;

    private TeamServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leadId = UUID.randomUUID();
    private final UUID brokerId = UUID.randomUUID();
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new TeamServiceImpl(teamRepo, roleRepo, roleResolver, personMapper);
        admin = new AuthenticatedUser(UUID.randomUUID(), tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
    }

    private void stubPersonMapper() {
        when(personMapper.toResponse(any(ModAuthUserRoleEntity.class)))
                .thenAnswer(inv -> {
                    ModAuthUserRoleEntity r = inv.getArgument(0);
                    return new PersonResponse(r.getUserId(), r.getName(), null, r.getRole(), r.getTeamId(), r.getTeamName(), true);
                });
    }

    private ModAuthUserRoleEntity roleRow(UUID userId, UUID tenant, Role role) {
        return ModAuthUserRoleEntity.builder().userId(userId).tenantId(tenant).role(role).acceptedTermsVersion(1).build();
    }

    @Test
    void createBuildsTeamAndLinksLeadAndInitialBrokers() {
        ModAuthUserRoleEntity lead = roleRow(leadId, tenantId, Role.TEAM_LEAD);
        ModAuthUserRoleEntity broker = roleRow(brokerId, tenantId, Role.BROKER);
        when(roleRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(roleRepo.findById(brokerId)).thenReturn(Optional.of(broker));
        when(teamRepo.existsByTeamLeadUserId(leadId)).thenReturn(false);
        stubPersonMapper();

        TeamResponse response = service.create(admin, new CreateTeamRequest("North Region", leadId, List.of(brokerId)));

        assertThat(response.name()).isEqualTo("North Region");
        assertThat(lead.getTeamId()).isNotNull();
        assertThat(broker.getTeamId()).isEqualTo(lead.getTeamId());
        verify(roleRepo).save(lead);
        verify(roleRepo).save(broker);
        ArgumentCaptor<TeamEntity> captor = ArgumentCaptor.forClass(TeamEntity.class);
        verify(teamRepo).save(captor.capture());
        assertThat(captor.getValue().getTeamLeadUserId()).isEqualTo(leadId);
    }

    @Test
    void cannotCreateTeamForALeadWhoAlreadyRunsOne() {
        when(roleRepo.findById(leadId)).thenReturn(Optional.of(roleRow(leadId, tenantId, Role.TEAM_LEAD)));
        when(teamRepo.existsByTeamLeadUserId(leadId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(admin, new CreateTeamRequest("North Region", leadId, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already runs a team");
    }

    @Test
    void cannotCreateTeamWithABrokerAsTheLead() {
        when(roleRepo.findById(leadId)).thenReturn(Optional.of(roleRow(leadId, tenantId, Role.BROKER)));

        assertThatThrownBy(() -> service.create(admin, new CreateTeamRequest("North Region", leadId, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Expected a TEAM_LEAD");
    }

    @Test
    void cannotUseAPersonFromAnotherTenantAsLead() {
        when(roleRepo.findById(leadId)).thenReturn(Optional.of(roleRow(leadId, UUID.randomUUID(), Role.TEAM_LEAD)));

        assertThatThrownBy(() -> service.create(admin, new CreateTeamRequest("North Region", leadId, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your brokerage's person");
    }

    @Test
    void addMemberRequiresBrokerRole() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleRepo.findById(leadId)).thenReturn(Optional.of(roleRow(leadId, tenantId, Role.TEAM_LEAD)));

        assertThatThrownBy(() -> service.addMember(admin, team.getId(), leadId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Expected a BROKER");
    }

    @Test
    void addMemberAcceptsAnAssistantToo() {
        UUID assistantId = UUID.randomUUID();
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        ModAuthUserRoleEntity assistant = roleRow(assistantId, tenantId, Role.ASSISTANT);
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleRepo.findById(assistantId)).thenReturn(Optional.of(assistant));

        service.addMember(admin, team.getId(), assistantId);

        assertThat(assistant.getTeamId()).isEqualTo(team.getId());
        verify(roleRepo).save(assistant);
    }

    @Test
    void removeMemberClearsTeamIdButKeepsRoleAndAccount() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        ModAuthUserRoleEntity broker = roleRow(brokerId, tenantId, Role.BROKER);
        broker.setTeamId(team.getId());
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleRepo.findById(brokerId)).thenReturn(Optional.of(broker));

        service.removeMember(admin, team.getId(), brokerId);

        assertThat(broker.getTeamId()).isNull();
        assertThat(broker.getRole()).isEqualTo(Role.BROKER);
        verify(roleRepo).save(broker);
    }

    @Test
    void cannotRemoveTheTeamLeadViaMemberRemove() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.removeMember(admin, team.getId(), leadId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("delete the team instead");

        verify(roleRepo, never()).save(any());
    }

    @Test
    void deleteBlockedWhileMembersExist() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleRepo.findByTeamId(team.getId())).thenReturn(List.of(roleRow(leadId, tenantId, Role.TEAM_LEAD)));

        assertThatThrownBy(() -> service.delete(admin, team.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Remove all members");

        verify(teamRepo, never()).delete(any());
    }

    @Test
    void deleteSucceedsOnceEmpty() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleRepo.findByTeamId(team.getId())).thenReturn(List.of());

        service.delete(admin, team.getId());

        verify(teamRepo, times(1)).delete(team);
    }

    @Test
    void cannotActOnAnotherTenantsTeam() {
        TeamEntity otherTenantsTeam = TeamEntity.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID())
                .name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(otherTenantsTeam.getId())).thenReturn(Optional.of(otherTenantsTeam));

        assertThatThrownBy(() -> service.get(admin, otherTenantsTeam.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your brokerage's team");
    }

    @Test
    void tenantAdminCanViewAnyTeamInTheirTenant() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(admin)).thenReturn(Role.TENANT_ADMIN);

        TeamResponse response = service.get(admin, team.getId());

        assertThat(response.id()).isEqualTo(team.getId());
    }

    @Test
    void theTeamsOwnLeadCanViewItsRoster() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        AuthenticatedUser leadCaller = new AuthenticatedUser(leadId, tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(leadCaller)).thenReturn(Role.TEAM_LEAD);

        TeamResponse response = service.get(leadCaller, team.getId());

        assertThat(response.id()).isEqualTo(team.getId());
    }

    @Test
    void aDifferentTeamsLeadCannotViewThisRoster() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        UUID otherLeadId = UUID.randomUUID();
        AuthenticatedUser otherLeadCaller = new AuthenticatedUser(otherLeadId, tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(otherLeadCaller)).thenReturn(Role.TEAM_LEAD);

        assertThatThrownBy(() -> service.get(otherLeadCaller, team.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not allowed to view this team");
    }

    @Test
    void aBrokerCannotViewTheTeamRoster() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(admin)).thenReturn(Role.BROKER);

        assertThatThrownBy(() -> service.get(admin, team.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not allowed to view this team");
    }

    @Test
    void anAssistantOnTheTeamCanViewItsRoster() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        UUID assistantId = UUID.randomUUID();
        ModAuthUserRoleEntity assistant = roleRow(assistantId, tenantId, Role.ASSISTANT);
        assistant.setTeamId(team.getId());
        AuthenticatedUser caller = new AuthenticatedUser(assistantId, tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(caller)).thenReturn(Role.ASSISTANT);
        when(roleRepo.findById(assistantId)).thenReturn(Optional.of(assistant));

        assertThat(service.get(caller, team.getId()).id()).isEqualTo(team.getId());
    }

    @Test
    void anAssistantOnAnotherTeamCannotViewThisRoster() {
        TeamEntity team = TeamEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("X").teamLeadUserId(leadId).build();
        UUID assistantId = UUID.randomUUID();
        ModAuthUserRoleEntity assistant = roleRow(assistantId, tenantId, Role.ASSISTANT);
        assistant.setTeamId(UUID.randomUUID());
        AuthenticatedUser caller = new AuthenticatedUser(assistantId, tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
        when(teamRepo.findById(team.getId())).thenReturn(Optional.of(team));
        when(roleResolver.resolve(caller)).thenReturn(Role.ASSISTANT);
        when(roleRepo.findById(assistantId)).thenReturn(Optional.of(assistant));

        assertThatThrownBy(() -> service.get(caller, team.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not allowed to view this team");
    }
}
