package com.example.modauth.service;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.AccountLockedException;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.ModLoginRequest;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks in the "Logging in" flow diagram's exact decision order — lockout,
 * then credentials, then account-active, then terms, then role — one test
 * per branch, mocking gen-auth-starter's own building blocks so this only
 * exercises ModAuthLoginServiceImpl's own orchestration.
 */
@ExtendWith(MockitoExtension.class)
class ModAuthLoginServiceImplTest {

    @Mock private TenantAccessService tenantAccessService;
    @Mock private PlatformEventClient platformEvents;
    @Mock private ModAuthUserLookupRepository userLookupRepo;
    @Mock private ModAuthUserRoleJpaRepository roleRepo;
    @Mock private LockoutService lockoutService;
    @Mock private PasswordHasher passwordHasher;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private JavaMailSender mailSender;

    private ModAuthLoginServiceImpl service;

    private static final String EMAIL = "person@example.com";
    private static final String PASSWORD = "correct-password";
    private static final String IP = "127.0.0.1";
    private static final String UA = "test-agent";

    @BeforeEach
    void setUp() {
        service = new ModAuthLoginServiceImpl(userLookupRepo, roleRepo, lockoutService, passwordHasher, loginExecutor,
                tenantAccessService, platformEvents);
        ReflectionTestUtils.setField(service, "currentTermsVersion", 2);
    }

    private ModLoginRequest request(boolean acceptTerms) {
        return new ModLoginRequest(EMAIL, PASSWORD, acceptTerms);
    }

    private AuthUserEntity activeUser() {
        return AuthUserEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash("hashed")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();
    }

    @Test
    void lockedOutAccountNeverReachesCredentialCheck() {
        doThrow(new AccountLockedException()).when(lockoutService).checkLockout(EMAIL, IP);

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(AccountLockedException.class);

        verify(userLookupRepo, never()).findByEmail(anyString());
    }

    @Test
    void wrongPasswordRecordsFailureAndThrows() {
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(false);

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginExecutor).handleFailure(user.getTenantId(), user.getId(), EMAIL, IP, UA, "INVALID_CREDENTIALS");
        verify(lockoutService, never()).recordFailure(anyString(), anyString());
    }

    @Test
    void failureThatJustTriggersLockoutSendsAWarningEmail() {
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(false);
        // First call (top of login) proves not-yet-locked; second call (right after
        // handleFailure) throws — the exact transition this attempt just caused.
        doNothing().doThrow(new AccountLockedException()).when(lockoutService).checkLockout(EMAIL, IP);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void failureThatDoesNotTriggerLockoutSendsNoEmail() {
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(false);
        // Both checkLockout calls succeed — still has failures left, not locked yet.

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void unknownEmailStillGoesThroughHandleFailureWithNullUser() {
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginExecutor).handleFailure(isNull(), isNull(), eq(EMAIL), eq(IP), eq(UA), eq("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivatedAccountIsBlockedAfterPasswordSucceeds() {
        AuthUserEntity user = activeUser();
        user.setActive(false);
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(false), IP, UA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Contact your admin");

        verify(lockoutService, never()).clearFailure(anyString(), anyString());
    }

    @Test
    void termsChangedWithoutAcceptanceReturnsGateNotTokens() {
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);
        ModAuthUserRoleEntity roleRow = ModAuthUserRoleEntity.builder()
                .userId(user.getId()).tenantId(user.getTenantId()).role(Role.BROKER).acceptedTermsVersion(1).build();
        when(roleRepo.findById(user.getId())).thenReturn(Optional.of(roleRow));

        ModAuthLoginResult result = service.login(request(false), IP, UA);

        assertThat(result.requiresTermsAcceptance()).isTrue();
        assertThat(result.termsVersion()).isEqualTo(2);
        assertThat(result.starterResult()).isNull();
        verify(loginExecutor, never()).issueTokens(any(), any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(roleRepo, never()).save(any());
    }

    @Test
    void termsChangedWithAcceptanceRecordsAndProceeds() {
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);
        ModAuthUserRoleEntity roleRow = ModAuthUserRoleEntity.builder()
                .userId(user.getId()).tenantId(user.getTenantId()).role(Role.BROKER).acceptedTermsVersion(1).build();
        when(roleRepo.findById(user.getId())).thenReturn(Optional.of(roleRow));
        when(loginExecutor.issueTokens(eq(user), any(), eq(IP), eq(UA), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(new LoginResponse(UUID.randomUUID(), EMAIL, java.time.Instant.now()), "access", "refresh", Duration.ofMinutes(20), Duration.ofDays(7)));

        ModAuthLoginResult result = service.login(request(true), IP, UA);

        assertThat(result.requiresTermsAcceptance()).isFalse();
        assertThat(result.role()).isEqualTo(Role.BROKER);
        assertThat(result.dashboard()).isEqualTo("broker_dashboard");
        assertThat(roleRow.getAcceptedTermsVersion()).isEqualTo(2);
        verify(roleRepo).save(roleRow);
    }

    @Test
    void superAdminWithNoRoleRowStillLogsInViaUserType() {
        AuthUserEntity user = activeUser();
        user.setUserType(UserType.SUPER_ADMIN);
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);
        when(roleRepo.findById(user.getId())).thenReturn(Optional.empty());
        when(loginExecutor.issueTokens(eq(user), any(), eq(IP), eq(UA), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(new LoginResponse(UUID.randomUUID(), EMAIL, java.time.Instant.now()), "access", "refresh", Duration.ofMinutes(20), Duration.ofDays(7)));

        ModAuthLoginResult result = service.login(request(true), IP, UA);

        assertThat(result.role()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(result.dashboard()).isEqualTo("platform_console");
    }

    @Test
    void successfulLoginClearsFailureCounter() {
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);
        ModAuthUserRoleEntity roleRow = ModAuthUserRoleEntity.builder()
                .userId(user.getId()).tenantId(user.getTenantId()).role(Role.TENANT_ADMIN).acceptedTermsVersion(2).build();
        when(roleRepo.findById(user.getId())).thenReturn(Optional.of(roleRow));
        when(loginExecutor.issueTokens(eq(user), any(), eq(IP), eq(UA), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(new LoginResponse(UUID.randomUUID(), EMAIL, java.time.Instant.now()), "access", "refresh", Duration.ofMinutes(20), Duration.ofDays(7)));

        service.login(request(false), IP, UA);

        verify(lockoutService).clearFailure(EMAIL, IP);
    }

    @Test
    void suspendedBrokerageIsBlockedAtLoginAndNoTokensAreIssued() {
        AuthUserEntity user = activeUser();
        when(userLookupRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(eq(PASSWORD), eq("hashed"))).thenReturn(true);
        when(tenantAccessService.isBlocked(user.getTenantId())).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(true), IP, UA))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("suspended");

        verify(loginExecutor, never()).issueTokens(any(), any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(platformEvents).send(eq("security.login_blocked_suspended_brokerage"), eq(user.getTenantId()),
                eq(user.getId()), eq(IP), org.mockito.ArgumentMatchers.isNull());
    }
}
