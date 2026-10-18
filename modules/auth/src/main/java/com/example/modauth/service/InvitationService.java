package com.example.modauth.service;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.AcceptInvitationRequest;
import com.example.modauth.dto.AcceptInvitationResponse;
import com.example.modauth.dto.CreateInvitationRequest;
import com.example.modauth.dto.InternalCreateInvitationRequest;
import com.example.modauth.dto.InvitationPreviewResponse;
import com.example.modauth.dto.InvitationResponse;

import java.util.UUID;

public interface InvitationService {

    InvitationResponse create(AuthenticatedUser inviter, CreateInvitationRequest request);

    /** Platform-triggered TENANT_ADMIN (brokerage owner) invite — see InternalCreateInvitationRequest. */
    InvitationResponse createForBrokerageOwner(InternalCreateInvitationRequest request);

    InvitationResponse resend(AuthenticatedUser inviter, UUID invitationId);

    /** "Resend, or cancel the invitation" while a brokerage owner hasn't accepted yet. */
    void cancel(AuthenticatedUser inviter, UUID invitationId);

    InvitationPreviewResponse preview(String rawToken);

    AcceptInvitationResponse accept(AcceptInvitationRequest request);
}
