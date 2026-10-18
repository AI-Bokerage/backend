package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Completes invitation acceptance once {@link InvitationServiceImpl#accept}'s
 * token lookup has resolved the invitation's own tenantId. Runs with no
 * {@code GenAdmPrincipal} in scope (public, token-authenticated endpoint),
 * so tenant context comes from an explicit {@code @TenantIdParam} argument,
 * exactly like Phase 2's {@code OffboardingStepExecutor}. A genuinely
 * separate {@code @Service} bean (not a private method on
 * {@code InvitationServiceImpl}) — self-invocation within one class bypasses
 * Spring's AOP proxy, which would silently skip {@code TenantContextAspect}
 * for this call.
 */
@Service
public class InvitationAcceptanceExecutor {

    private final InvitationRepository invitationRepository;
    private final UserRoleAssignmentService userRoleAssignmentService;

    public InvitationAcceptanceExecutor(InvitationRepository invitationRepository,
                                         UserRoleAssignmentService userRoleAssignmentService) {
        this.invitationRepository = invitationRepository;
        this.userRoleAssignmentService = userRoleAssignmentService;
    }

    @Transactional
    public InvitationEntity completeAcceptance(@TenantIdParam UUID tenantId, UUID invitationId, UUID userId) {
        InvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found: " + invitationId));

        for (UUID roleId : invitation.getRoleIds()) {
            userRoleAssignmentService.assignRoleFromInvitation(tenantId, userId, roleId);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        return invitationRepository.saveAndFlush(invitation);
    }
}
