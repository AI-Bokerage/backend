package com.example.modauth.controller;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.service.PeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implements the "People" flow diagram — list + deactivate Team Leads/Brokers. Tenant Admin only. */
@Tag(name = "People", description = "Tenant Admin: list and deactivate the Team Leads/Brokers in a brokerage — a Tenant Admin gets 403 until the brokerage profile is complete (CRM PATCH /api/v1/crm/tenant/profile)")
@RestController
@RequestMapping("/api/v1/modauth/people")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PeopleController {

    private final PeopleService peopleService;

    @Operation(summary = "List everyone in the caller's brokerage (Team Leads and Brokers)")
    @GetMapping
    public List<PersonResponse> list(@AuthenticationPrincipal AuthenticatedUser admin) {
        return peopleService.list(admin);
    }

    @Operation(summary = "Deactivate a person", description = "Blocked with 409 \"Reassign their N clients first\" "
            + "while they still hold any lead or client (checked live against the CRM — reassign with the CRM's "
            + "POST /api/v1/crm/tenant/people/{userId}/reassign-clients). Otherwise deactivates the account and "
            + "revokes all sessions.")
    @ApiResponse(responseCode = "404", description = "Person not found")
    @ApiResponse(responseCode = "403", description = "Not your brokerage's person, or target is a Tenant Admin")
    @PatchMapping("/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID userId) {
        peopleService.deactivate(admin, userId);
    }
}
