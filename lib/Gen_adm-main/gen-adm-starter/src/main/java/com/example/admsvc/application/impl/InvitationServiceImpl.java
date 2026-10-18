package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final InvitationEventPublisher eventPublisher;
    private final GenAdmProperties properties;
    private final InvitationAcceptanceExecutor acceptanceExecutor;

    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                  RoleRepository roleRepository,
                                  InvitationEventPublisher eventPublisher,
                                  GenAdmProperties properties,
                                  InvitationAcceptanceExecutor acceptanceExecutor) {
        this.invitationRepository = invitationRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.acceptanceExecutor = acceptanceExecutor;
    }

    @Override
    @Transactional
    public InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            if (!roleRepository.existsByIdAndTenantId(roleId, tenantId)) {
                throw new GenAdmValidationException("Role " + roleId + " does not exist in this tenant.");
            }
        }

        invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, email, InvitationStatus.PENDING)
                .map(this::expireIfOverdue)
                .filter(existing -> existing.getStatus() == InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new GenAdmConflictException("An active invitation already exists for " + email);
                });

        String plaintextToken = UUID.randomUUID().toString();
        InvitationEntity invitation = InvitationEntity.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(hashToken(plaintextToken))
                .invitedByUserId(invitedByUserId)
                .expiresAt(Instant.now().plus(properties.getInvitationTtlDays(), ChronoUnit.DAYS))
                .plaintextToken(plaintextToken)
                .build();
        invitation.setRoleIds(roleIds);

        InvitationEntity saved = invitationRepository.saveAndFlush(invitation);
        eventPublisher.onCreated(tenantId, saved.getId(), email, plaintextToken);
        return saved;
    }

    @Override
    @Transactional
    public List<InvitationEntity> listActionable(UUID tenantId) {
        return invitationRepository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING).stream()
                .map(this::expireIfOverdue)
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
                .toList();
    }

    @Override
    public InvitationEntity accept(String token, UUID userId) {
        String tokenHash = hashToken(token);
        InvitationEntity invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found or already used."));

        invitation = expireIfOverdue(invitation);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new GenAdmConflictException("This invitation is no longer pending.");
        }

        return acceptanceExecutor.completeAcceptance(invitation.getTenantId(), invitation.getId(), userId);
    }

    @Override
    @Transactional
    public InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId) {
        InvitationEntity invitation = expireIfOverdue(findOrThrow(tenantId, invitationId));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new GenAdmConflictException("Invitation is not pending: " + invitationId);
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setCancelledAt(Instant.now());
        invitation.setCancelledByUserId(cancelledByUserId);
        return invitationRepository.saveAndFlush(invitation);
    }

    private InvitationEntity findOrThrow(UUID tenantId, UUID invitationId) {
        return invitationRepository.findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found: " + invitationId));
    }

    private InvitationEntity expireIfOverdue(InvitationEntity invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING && Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            return invitationRepository.saveAndFlush(invitation);
        }
        return invitation;
    }

    private static String hashToken(String plaintext) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
