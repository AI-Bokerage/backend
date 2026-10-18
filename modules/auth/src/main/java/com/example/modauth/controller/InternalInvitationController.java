package com.example.modauth.controller;

import com.example.modauth.dto.InternalCreateInvitationRequest;
import com.example.modauth.dto.InvitationResponse;
import com.example.modauth.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service counterpart of {@link InvitationController#create}, for
 * callers that aren't an authenticated modauth user with a role — namely
 * modules/platform, right after it creates a Gen_TNT tenant record for a new
 * brokerage. Lives under {@code /internal/**}, so it's already gated by
 * gen-auth-starter's {@code InternalTokenAuthFilter} (shared-secret header)
 * exactly like {@code /internal/auth/users} — no JWT, no role check.
 */
@Tag(name = "Internal — Invitations", description = "Service-to-service brokerage-owner invite creation")
@RestController
@RequestMapping("/internal/modauth/invitations")
@RequiredArgsConstructor
public class InternalInvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "Create a brokerage-owner (TENANT_ADMIN) invitation",
            description = "Called by modules/platform right after it creates the Gen_TNT tenant record.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@Valid @RequestBody InternalCreateInvitationRequest request) {
        return invitationService.createForBrokerageOwner(request);
    }
}
