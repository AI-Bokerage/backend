package com.example.modauth.service;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.application.impl.ChangePasswordServiceImpl;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.entity.PasswordHistoryEntity;
import com.example.modauth.repository.PasswordHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SOW 1.4 — 12-char minimum, no reuse of current or last 5 passwords. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordPolicyTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private PasswordHistoryJpaRepository historyRepo;
    @Mock private PasswordHasher passwordHasher;
    @Mock private ChangePasswordServiceImpl starterChangePassword;
    @Mock private PlatformEventClient platformEvents;

    private final UUID userId = UUID.randomUUID();
    private PasswordPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PasswordPolicy(userRepo, historyRepo, passwordHasher);
        AuthUserEntity user = new AuthUserEntity();
        user.setPasswordHash("hash-current");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(historyRepo.findTop5ByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(PasswordHistoryEntity.builder().passwordHash("hash-old").build()));
        // Fake hasher: "hash-X" matches plaintext "X-long-enough".
        when(passwordHasher.verify(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1).equals("hash-" + inv.getArgument(0).toString().replace("-long-enough", "")));
    }

    @Test
    void rejectsPasswordsShorterThan12() {
        assertThatThrownBy(() -> policy.requireAcceptable(userId, "elevenchars"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void rejectsTheCurrentAndARecentPassword() {
        assertThatThrownBy(() -> policy.requireAcceptable(userId, "current-long-enough"))
                .hasMessageContaining("reuse");
        assertThatThrownBy(() -> policy.requireAcceptable(userId, "old-long-enough"))
                .hasMessageContaining("reuse");
    }

    @Test
    void acceptsANewPasswordAndReturnsTheCurrentHashToRemember() {
        assertThat(policy.requireAcceptable(userId, "brand-new-long-enough")).isEqualTo("hash-current");

        policy.remember(userId, "hash-current");

        verify(historyRepo).save(any(PasswordHistoryEntity.class));
    }

    @Test
    void changePasswordWrapperSkipsThePolicyWhenTheCurrentPasswordIsWrong() {
        var wrapper = new PolicyChangePasswordService(starterChangePassword, policy, userRepo, passwordHasher, platformEvents);
        var principal = new AuthenticatedUser(userId, UUID.randomUUID(), "acme", List.of(), UserType.TENANT_USER,
                UUID.randomUUID().toString(), null, "jti");
        // Wrong current password, reused new one: no reuse oracle, the starter rejects it.
        var request = new ChangePasswordRequest("wrong-long-enough", "old-long-enough", "old-long-enough");

        wrapper.changePassword(principal, request);

        verify(starterChangePassword).changePassword(principal, request);
        verify(historyRepo, never()).save(any());
    }

    @Test
    void changePasswordWrapperBlocksReuseBeforeTheStarterRuns() {
        var wrapper = new PolicyChangePasswordService(starterChangePassword, policy, userRepo, passwordHasher, platformEvents);
        var principal = new AuthenticatedUser(userId, UUID.randomUUID(), "acme", List.of(), UserType.TENANT_USER,
                UUID.randomUUID().toString(), null, "jti");
        var request = new ChangePasswordRequest("current-long-enough", "old-long-enough", "old-long-enough");

        assertThatThrownBy(() -> wrapper.changePassword(principal, request)).hasMessageContaining("reuse");
        verify(starterChangePassword, never()).changePassword(any(), any());
        verify(historyRepo, never()).save(any());
        verify(passwordHasher).verify(eq("current-long-enough"), eq("hash-current"));
    }
}
