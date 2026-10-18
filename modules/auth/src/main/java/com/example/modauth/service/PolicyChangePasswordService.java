package com.example.modauth.service;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.application.impl.ChangePasswordServiceImpl;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The starter's change-password flow, with modauth's PasswordPolicy (SOW 1.4) applied first. */
@Primary
@Service
@RequiredArgsConstructor
public class PolicyChangePasswordService implements ChangePasswordService {

    private final ChangePasswordServiceImpl delegate;
    private final PasswordPolicy passwordPolicy;
    private final AuthUserJpaRepository userRepo;
    private final PasswordHasher passwordHasher;
    private final PlatformEventClient platformEvents;

    @Override
    @Transactional
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        // Policy only after the current password checks out — otherwise the
        // reuse check would let a stolen session probe old passwords. A wrong
        // current password goes straight to the starter for its own 401.
        boolean currentPasswordOk = principal != null && userRepo.findById(principal.getUserId())
                .map(u -> passwordHasher.verify(request.currentPassword(), u.getPasswordHash()))
                .orElse(false);
        if (!currentPasswordOk) {
            delegate.changePassword(principal, request);
            return;
        }
        String previousHash = passwordPolicy.requireAcceptable(principal.getUserId(), request.newPassword());
        delegate.changePassword(principal, request);
        passwordPolicy.remember(principal.getUserId(), previousHash);
        platformEvents.send("auth.password.changed", principal.getTenantId(), principal.getUserId(), null, null);
    }
}
