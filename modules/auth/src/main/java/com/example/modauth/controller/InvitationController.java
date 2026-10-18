package com.example.modauth.controller;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.AcceptInvitationRequest;
import com.example.modauth.dto.AcceptInvitationResponse;
import com.example.modauth.dto.CreateInvitationRequest;
import com.example.modauth.dto.InvitationPreviewResponse;
import com.example.modauth.dto.InvitationResponse;
import com.example.modauth.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.UUID;

/**
 * Implements the "Inviting someone" (create/resend, JWT-authenticated) and
 * "Accepting an invitation" (preview/accept, public — the caller has no
 * session yet) flow diagrams.
 */
@Tag(name = "Invitations", description = "Invite a Tenant Admin, Team Lead, Broker or Assistant; accept an invitation — a Tenant Admin gets 403 until the brokerage profile is complete (CRM PATCH /api/v1/crm/tenant/profile)")
@RestController
@RequestMapping("/api/v1/modauth/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "Invite someone", description = "Who can invite whom follows the platform hierarchy: "
            + "Super Admin -> Tenant Admin -> Team Lead -> Broker, one level down at a time.")
    @ApiResponse(responseCode = "201", description = "Invitation created, status PENDING")
    @ApiResponse(responseCode = "403", description = "Caller's role can't invite that role")
    @ApiResponse(responseCode = "409", description = "This person already has an account here")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@AuthenticationPrincipal AuthenticatedUser inviter,
                                      @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(inviter, request);
    }

    @Operation(summary = "Resend an invitation", description = "Issues a fresh token and expiry for a still-pending invitation.")
    @ApiResponse(responseCode = "200", description = "Invitation re-sent")
    @ApiResponse(responseCode = "409", description = "Invitation was already accepted")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/resend")
    public InvitationResponse resend(@AuthenticationPrincipal AuthenticatedUser inviter,
                                      @PathVariable UUID id) {
        return invitationService.resend(inviter, id);
    }

    @Operation(summary = "Cancel an invitation", description = "\"Resend, or cancel the invitation\" while it's still pending.")
    @ApiResponse(responseCode = "204", description = "Invitation cancelled")
    @ApiResponse(responseCode = "409", description = "Invitation was already accepted")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthenticatedUser inviter, @PathVariable UUID id) {
        invitationService.cancel(inviter, id);
    }

    @Operation(summary = "Preview an invitation", description = "Public — reached straight from the invitation email, "
            + "before the recipient has any session. Powers the \"is the link still valid?\" check.")
    @ApiResponse(responseCode = "200", description = "Link is valid; shows name/email/role/team for the accept screen")
    @ApiResponse(responseCode = "400", description = "Invitation link is invalid, expired, or already used")
    @GetMapping("/{token}")
    public InvitationPreviewResponse preview(@Parameter(description = "Raw token from the invitation email")
                                              @PathVariable String token) {
        return invitationService.preview(token);
    }

    @Operation(summary = "Accept an invitation", description = "Public. Sets the password, requires terms acceptance, "
            + "creates the account, and returns where the frontend should send the user next.")
    @ApiResponse(responseCode = "200", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Invalid/expired link, password rule violation, or terms not accepted")
    @PostMapping("/{token}/accept")
    public AcceptInvitationResponse accept(@Parameter(description = "Same token as the path — kept for a self-describing URL")
                                            @PathVariable String token,
                                            @Valid @RequestBody AcceptInvitationRequest request) {
        // token also lives in the body (AcceptInvitationRequest.token()) so it can
        // carry the @NotBlank/@PasswordsMatch validation group; the path variable
        // just makes the URL self-describing. Body wins if they ever disagree.
        return invitationService.accept(request);
    }
}
