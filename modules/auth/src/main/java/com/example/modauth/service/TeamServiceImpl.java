package com.example.modauth.service;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.CreateTeamRequest;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.dto.TeamResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.entity.TeamEntity;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TeamJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamJpaRepository teamRepo;
    private final ModAuthUserRoleJpaRepository roleRepo;
    private final RoleResolver roleResolver;
    private final PersonMapper personMapper;

    @Override
    @Transactional
    public TeamResponse create(AuthenticatedUser admin, CreateTeamRequest request) {
        roleResolver.requireTenantAdmin(admin);
        UUID tenantId = admin.getTenantId();

        ModAuthUserRoleEntity lead = requireMemberOfRole(tenantId, request.teamLeadUserId(), Role.TEAM_LEAD);
        if (teamRepo.existsByTeamLeadUserId(lead.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This Team Lead already runs a team");
        }

        TeamEntity team = TeamEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(request.name())
                .teamLeadUserId(lead.getUserId())
                .build();
        teamRepo.save(team);

        lead.setTeamId(team.getId());
        roleRepo.save(lead);

        for (UUID brokerUserId : request.brokerUserIdsOrEmpty()) {
            ModAuthUserRoleEntity broker = requireMemberOfRole(tenantId, brokerUserId, Role.BROKER, Role.ASSISTANT);
            broker.setTeamId(team.getId());
            roleRepo.save(broker);
        }

        log.info("team.created id={} tenantId={} teamLeadUserId={}", team.getId(), tenantId, lead.getUserId());
        return toResponse(team);
    }

    @Override
    public List<TeamResponse> list(AuthenticatedUser admin) {
        roleResolver.requireTenantAdmin(admin);
        return teamRepo.findByTenantId(admin.getTenantId()).stream().map(this::toResponse).toList();
    }

    /**
     * Unlike every other method here, this one isn't Tenant-Admin-only: a
     * Team Lead can also fetch their own team's roster — modules/platform's
     * CRM module needs this (forwarding the caller's own JWT) to resolve
     * "who's on my team" for the Team Lead flow, without a second internal
     * service-to-service endpoint duplicating this same query. An Assistant
     * on the team can fetch it too (SOW 2.5): they work the whole team's book.
     */
    @Override
    public TeamResponse get(AuthenticatedUser caller, UUID teamId) {
        TeamEntity team = requireOwnedTeam(caller.getTenantId(), teamId);
        Role role = roleResolver.resolve(caller);
        boolean isThisTeamsLead = role == Role.TEAM_LEAD && caller.getUserId().equals(team.getTeamLeadUserId());
        boolean isThisTeamsAssistant = role == Role.ASSISTANT && roleRepo.findById(caller.getUserId())
                .map(r -> teamId.equals(r.getTeamId()))
                .orElse(false);
        if (role != Role.TENANT_ADMIN && !isThisTeamsLead && !isThisTeamsAssistant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this team");
        }
        return toResponse(team);
    }

    @Override
    @Transactional
    public TeamResponse addMember(AuthenticatedUser admin, UUID teamId, UUID brokerUserId) {
        roleResolver.requireTenantAdmin(admin);
        TeamEntity team = requireOwnedTeam(admin.getTenantId(), teamId);

        ModAuthUserRoleEntity broker = requireMemberOfRole(admin.getTenantId(), brokerUserId, Role.BROKER, Role.ASSISTANT);
        broker.setTeamId(team.getId());
        roleRepo.save(broker);

        log.info("team.member_added teamId={} userId={}", teamId, brokerUserId);
        return toResponse(team);
    }

    @Override
    @Transactional
    public TeamResponse removeMember(AuthenticatedUser admin, UUID teamId, UUID brokerUserId) {
        roleResolver.requireTenantAdmin(admin);
        TeamEntity team = requireOwnedTeam(admin.getTenantId(), teamId);

        if (brokerUserId.equals(team.getTeamLeadUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the team lead — delete the team instead");
        }
        ModAuthUserRoleEntity broker = roleRepo.findById(brokerUserId)
                .filter(r -> teamId.equals(r.getTeamId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this team"));

        // ponytail: no client-reassignment here by design — a removed Broker keeps
        // their clients and just leaves the team view, per the flow diagram.
        broker.setTeamId(null);
        roleRepo.save(broker);

        log.info("team.member_removed teamId={} userId={}", teamId, brokerUserId);
        return toResponse(team);
    }

    @Override
    @Transactional
    public void delete(AuthenticatedUser admin, UUID teamId) {
        roleResolver.requireTenantAdmin(admin);
        TeamEntity team = requireOwnedTeam(admin.getTenantId(), teamId);

        if (!roleRepo.findByTeamId(teamId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove all members (including the Team Lead) before deleting this team");
        }
        teamRepo.delete(team);
        log.info("team.deleted id={}", teamId);
    }

    private TeamEntity requireOwnedTeam(UUID tenantId, UUID teamId) {
        TeamEntity team = teamRepo.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (!team.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your brokerage's team");
        }
        return team;
    }

    private ModAuthUserRoleEntity requireMemberOfRole(UUID tenantId, UUID userId, Role... expectedRoles) {
        ModAuthUserRoleEntity row = roleRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No such person"));
        if (!row.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your brokerage's person");
        }
        if (!List.of(expectedRoles).contains(row.getRole())) {
            String expected = Arrays.stream(expectedRoles).map(Role::name).collect(Collectors.joining(" or "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected a " + expected + " but this person is a " + row.getRole());
        }
        return row;
    }

    private TeamResponse toResponse(TeamEntity team) {
        PersonResponse lead = roleRepo.findById(team.getTeamLeadUserId())
                .map(personMapper::toResponse)
                .orElse(null);
        List<PersonResponse> members = roleRepo.findByTeamId(team.getId()).stream()
                .filter(r -> !r.getUserId().equals(team.getTeamLeadUserId()))
                .map(personMapper::toResponse)
                .toList();
        return new TeamResponse(team.getId(), team.getName(), lead, members);
    }
}
