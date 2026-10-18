package com.example.modauth.service;

import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import com.example.modauth.domain.InvitationStatus;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.AcceptInvitationRequest;
import com.example.modauth.dto.AcceptInvitationResponse;
import com.example.modauth.dto.CreateInvitationRequest;
import com.example.modauth.dto.InternalCreateInvitationRequest;
import com.example.modauth.dto.InvitationPreviewResponse;
import com.example.modauth.dto.InvitationResponse;
import com.example.modauth.entity.InvitationEntity;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.entity.TeamEntity;
import com.example.modauth.repository.InvitationJpaRepository;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TeamJpaRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Implements the "Inviting someone" and "Accepting an invitation" flow
 * diagrams. Token issuing/hashing follows the exact same pattern as
 * gen-auth-starter's own {@code MagicLinkServiceImpl} (SecureRandom 48 bytes,
 * Base64-URL, SHA-256 hash at rest, single-use) — the invitation link and the
 * password-reset link are the same kind of artifact, just pointed at
 * account-creation instead of account-recovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 48;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final InvitationJpaRepository invitationRepo;
    private final ModAuthUserRoleJpaRepository roleRepo;
    private final ModAuthUserLookupRepository userLookupRepo;
    private final TeamJpaRepository teamRepo;
    private final RegisterService registerService;
    private final RoleResolver roleResolver;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${modauth.invitation.ttl-days:7}")
    private int ttlDays;

    @Value("${modauth.invitation.frontend-accept-url:http://localhost:3000/invitations/accept}")
    private String frontendAcceptUrl;

    @Value("${modauth.terms.current-version:1}")
    private int currentTermsVersion;

    /** Empty by default: platform-webhook notification is only meaningful when modules/platform is actually deployed. */
    @Value("${modauth.platform-webhook-url:}")
    private String platformWebhookUrl;

    @Value("${internal-service-secret}")
    private String internalServiceSecret;

    private static final HttpClient WEBHOOK_CLIENT = HttpClient.newHttpClient();

    @Override
    @Transactional
    public InvitationResponse create(AuthenticatedUser inviter, CreateInvitationRequest request) {
        Role inviterRole = roleResolver.resolve(inviter);
        requireCanInvite(inviterRole, request.role());

        InvitationEntity invitation = buildInvitation(
                inviter.getTenantId(), inviter.getUserId(), request.name(), request.email(),
                request.role(), request.teamId(), null);
        invitationRepo.save(invitation);

        sendInvitationEmail(invitation);
        log.info("invitation.created id={} tenantId={} role={}", invitation.getId(), invitation.getTenantId(), invitation.getRole());

        return toResponse(invitation);
    }

    @Override
    @Transactional
    public InvitationResponse createForBrokerageOwner(InternalCreateInvitationRequest request) {
        InvitationEntity invitation = buildInvitation(
                request.tenantId(), TenantConstants.SERVICE_ACCOUNT_ID, request.name(), request.email(),
                Role.TENANT_ADMIN, null, request.preAllocatedUserId());
        invitationRepo.save(invitation);

        sendInvitationEmail(invitation);
        log.info("invitation.created_for_brokerage_owner id={} tenantId={}", invitation.getId(), invitation.getTenantId());

        return toResponse(invitation);
    }

    private InvitationEntity buildInvitation(UUID tenantId, UUID inviterUserId, String name, String email,
                                             Role role, UUID teamId, UUID preAllocatedUserId) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userLookupRepo.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person already has an account here");
        }

        // teamName is a denormalized display snapshot: real when teamId points at an
        // actual team, otherwise left null (a Team Lead has no team until one is
        // created for them — see TeamServiceImpl.create).
        String teamName = null;
        if (teamId != null) {
            TeamEntity team = teamRepo.findById(teamId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No such team"));
            if (!team.getTenantId().equals(tenantId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your brokerage's team");
            }
            teamName = team.getName();
        }

        InvitationEntity invitation = InvitationEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .inviterUserId(inviterUserId)
                .name(name)
                .email(normalizedEmail)
                .role(role)
                .teamId(teamId)
                .teamName(teamName)
                .preAllocatedUserId(preAllocatedUserId)
                .status(InvitationStatus.PENDING)
                .build();
        issueToken(invitation);
        return invitation;
    }

    @Override
    @Transactional
    public InvitationResponse resend(AuthenticatedUser inviter, UUID invitationId) {
        InvitationEntity invitation = requireManageableInvitation(inviter, invitationId);
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation already accepted");
        }

        issueToken(invitation);
        invitationRepo.save(invitation);
        sendInvitationEmail(invitation);
        log.info("invitation.resent id={}", invitation.getId());

        return toResponse(invitation);
    }

    @Override
    @Transactional
    public void cancel(AuthenticatedUser inviter, UUID invitationId) {
        InvitationEntity invitation = requireManageableInvitation(inviter, invitationId);
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation already accepted");
        }
        invitationRepo.delete(invitation);
        log.info("invitation.cancelled id={}", invitationId);
    }

    /**
     * Same tenant as the caller, OR the caller is a Super Admin — a
     * brokerage-owner invitation's inviter is a service account with the
     * brokerage's own tenantId, not the Super Admin's, so the plain
     * tenant-match check alone would lock Super Admin out of resending or
     * cancelling the very invitations they created a brokerage for.
     */
    private InvitationEntity requireManageableInvitation(AuthenticatedUser inviter, UUID invitationId) {
        InvitationEntity invitation = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));

        boolean sameTenant = invitation.getTenantId().equals(inviter.getTenantId());
        if (!sameTenant && roleResolver.resolve(inviter) != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your brokerage's invitation");
        }
        return invitation;
    }

    @Override
    public InvitationPreviewResponse preview(String rawToken) {
        InvitationEntity invitation = findValidPendingInvitation(rawToken);
        return new InvitationPreviewResponse(invitation.getName(), invitation.getEmail(), invitation.getRole(), invitation.getTeamName());
    }

    @Override
    @Transactional
    public AcceptInvitationResponse accept(AcceptInvitationRequest request) {
        InvitationEntity invitation = findValidPendingInvitation(request.token());

        UUID userId = registerService.register(
                invitation.getEmail(), request.newPassword(), invitation.getTenantId(), null,
                invitation.getPreAllocatedUserId(), UserType.TENANT_USER);

        ModAuthUserRoleEntity roleRow = ModAuthUserRoleEntity.builder()
                .userId(userId)
                .tenantId(invitation.getTenantId())
                .name(invitation.getName())
                .role(invitation.getRole())
                .teamId(invitation.getTeamId())
                .teamName(invitation.getTeamName())
                .acceptedTermsVersion(currentTermsVersion)
                .build();
        roleRepo.save(roleRow);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepo.save(invitation);

        String nextStep = invitation.getRole() == Role.TENANT_ADMIN
                ? "complete_brokerage_profile"
                : "connect_email_calendar_optional";

        if (invitation.getRole() == Role.TENANT_ADMIN) {
            notifyPlatformOwnerAccepted(invitation.getTenantId());
        }

        log.info("invitation.accepted id={} userId={}", invitation.getId(), userId);
        return new AcceptInvitationResponse(userId, invitation.getEmail(), invitation.getRole(),
                invitation.getRole().dashboardKey(), nextStep);
    }

    private InvitationEntity findValidPendingInvitation(String rawToken) {
        String tokenHash = RefreshTokenHashUtil.hash(rawToken);
        InvitationEntity invitation = invitationRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation link is invalid or has expired"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation link is invalid or has expired");
        }
        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation link is invalid or has expired");
        }
        return invitation;
    }

    private void issueToken(InvitationEntity invitation) {
        byte[] rawBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawToken = URL_ENCODER.encodeToString(rawBytes);
        invitation.setTokenHash(RefreshTokenHashUtil.hash(rawToken));
        invitation.setExpiresAt(Instant.now().plus(Duration.ofDays(ttlDays)));
        // Stashed only for the outgoing email built right after this call — never persisted or logged in prod.
        invitation.setPendingRawToken(rawToken);
    }

    private void sendInvitationEmail(InvitationEntity invitation) {
        String acceptUrl = frontendAcceptUrl + "?token=" + invitation.getPendingRawToken();

        // ponytail: plain JavaMailSender instead of a templated HTML email —
        // upgrade to gen-auth-starter's EmailProvider/EmailHtmlTemplate pattern
        // if this needs to match the rest of the product's email styling.
        if (mailSender == null) {
            log.info("invitation.email_skipped_no_mail_sender email={} acceptUrl={}", invitation.getEmail(), acceptUrl);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setTo(invitation.getEmail());
            helper.setSubject("You're invited to join the team");
            helper.setText("Hi " + invitation.getName() + ",\n\n"
                    + "You've been invited as a " + invitation.getRole() + ". Set up your account here:\n"
                    + acceptUrl + "\n\nThis link expires in " + ttlDays + " day(s).");
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("invitation.email_send_failed email={}", invitation.getEmail(), e);
        }
    }

    /**
     * Best-effort, fire-and-forget — same pattern as TenantsService.publishSafely
     * on the modules/platform side: a failure here shouldn't fail the owner's
     * accept request, it just means modules/platform's BrokerageOnboarding row
     * stays PENDING until someone notices (its own onboardingStatus is a
     * separate, non-blocking display concern, not this transaction's job).
     */
    private void notifyPlatformOwnerAccepted(UUID tenantId) {
        if (platformWebhookUrl == null || platformWebhookUrl.isBlank()) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(platformWebhookUrl))
                .header("Content-Type", "application/json")
                .header("X-Internal-Secret", internalServiceSecret)
                .POST(HttpRequest.BodyPublishers.ofString("{\"tenantId\":\"" + tenantId + "\"}"))
                .build();
        WEBHOOK_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(err -> {
                    log.warn("invitation.platform_webhook_failed tenantId={}", tenantId, err);
                    return null;
                });
    }

    // package-private (not private) so InvitationAuthorizationTest can exercise the matrix directly.
    static void requireCanInvite(Role inviterRole, Role inviteeRole) {
        boolean allowed = switch (inviterRole) {
            case SUPER_ADMIN -> inviteeRole == Role.TENANT_ADMIN;
            // SOW 2.10: "Tenant Admin creates fixed teams and assigns brokers,
            // assistants, and Team Leads."
            case TENANT_ADMIN -> inviteeRole == Role.TEAM_LEAD || inviteeRole == Role.BROKER
                    || inviteeRole == Role.ASSISTANT;
            case TEAM_LEAD -> inviteeRole == Role.BROKER;
            case BROKER, ASSISTANT -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    inviterRole + " cannot invite a " + inviteeRole);
        }
    }

    private static InvitationResponse toResponse(InvitationEntity invitation) {
        return new InvitationResponse(
                invitation.getId(), invitation.getName(), invitation.getEmail(), invitation.getRole(),
                invitation.getTeamName(), invitation.getTeamId(), invitation.getStatus(), invitation.getExpiresAt());
    }
}
