package com.example.modauth.service;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.CreateTeamRequest;
import com.example.modauth.dto.TeamResponse;

import java.util.List;
import java.util.UUID;

/** Implements the "Manage teams" flow diagram: create/list/detail, add/remove Brokers, delete-if-empty. */
public interface TeamService {

    TeamResponse create(AuthenticatedUser admin, CreateTeamRequest request);

    List<TeamResponse> list(AuthenticatedUser admin);

    TeamResponse get(AuthenticatedUser admin, UUID teamId);

    TeamResponse addMember(AuthenticatedUser admin, UUID teamId, UUID brokerUserId);

    TeamResponse removeMember(AuthenticatedUser admin, UUID teamId, UUID brokerUserId);

    void delete(AuthenticatedUser admin, UUID teamId);
}
