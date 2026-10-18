package com.example.modauth.controller;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.CreateTeamRequest;
import com.example.modauth.dto.TeamResponse;
import com.example.modauth.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implements the "Manage teams" flow diagram. Tenant Admin only — enforced in TeamServiceImpl. */
@Tag(name = "Teams", description = "Tenant Admin: create/list/manage teams of Brokers and Assistants under a Team Lead — a Tenant Admin gets 403 until the brokerage profile is complete (CRM PATCH /api/v1/crm/tenant/profile)")
@RestController
@RequestMapping("/api/v1/modauth/teams")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Create a team", description = "Name it and pick an existing, not-yet-leading Team Lead; optionally add existing Brokers.")
    @ApiResponse(responseCode = "201", description = "Team created")
    @ApiResponse(responseCode = "409", description = "That Team Lead already runs a team")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody CreateTeamRequest request) {
        return teamService.create(admin, request);
    }

    @Operation(summary = "List teams in the caller's brokerage")
    @GetMapping
    public List<TeamResponse> list(@AuthenticationPrincipal AuthenticatedUser admin) {
        return teamService.list(admin);
    }

    @Operation(summary = "Team detail — lead and current Broker members")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @GetMapping("/{teamId}")
    public TeamResponse get(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID teamId) {
        return teamService.get(admin, teamId);
    }

    @Operation(summary = "Add an existing Broker to the team")
    @PostMapping("/{teamId}/members/{brokerUserId}")
    public TeamResponse addMember(@AuthenticationPrincipal AuthenticatedUser admin,
                                   @PathVariable UUID teamId, @PathVariable UUID brokerUserId) {
        return teamService.addMember(admin, teamId, brokerUserId);
    }

    @Operation(summary = "Remove a Broker from the team", description = "The Broker keeps their role and clients — they just leave this team's view.")
    @DeleteMapping("/{teamId}/members/{brokerUserId}")
    public TeamResponse removeMember(@AuthenticationPrincipal AuthenticatedUser admin,
                                      @PathVariable UUID teamId, @PathVariable UUID brokerUserId) {
        return teamService.removeMember(admin, teamId, brokerUserId);
    }

    @Operation(summary = "Delete a team", description = "Blocked (409) while it still has members, including the Team Lead.")
    @ApiResponse(responseCode = "409", description = "Team still has members")
    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID teamId) {
        teamService.delete(admin, teamId);
    }
}
