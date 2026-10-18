package com.example.modauth.service;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.impl.MagicLinkServiceImpl;
import com.example.authsvc.application.service.MagicLinkService;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The starter's forgot-password (magic-link) reset, with modauth's PasswordPolicy (SOW 1.4) applied first. */
@Primary
@Service
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PolicyMagicLinkService implements MagicLinkService {

    private final MagicLinkServiceImpl delegate;
    private final MagicLinkStore magicLinkStore;
    private final PasswordPolicy passwordPolicy;

    @Override
    public MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip) {
        return delegate.issue(request, ip);
    }

    @Override
    @Transactional
    public MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request) {
        PasswordPolicy.requireMinLength(request.newPassword());
        // Peek (don't consume) the token's user so reuse is checked before the
        // starter overwrites the hash; a bad/expired token is left to the
        // starter's own error.
        UUID userId = magicLinkStore.find(RefreshTokenHashUtil.hash(request.token()))
                .map(MagicLinkEntry::userId)
                .orElse(null);
        String previousHash = userId == null ? null : passwordPolicy.requireAcceptable(userId, request.newPassword());
        MagicLinkVerifyResponse response = delegate.verify(request);
        if (userId != null) {
            passwordPolicy.remember(userId, previousHash);
        }
        return response;
    }
}
