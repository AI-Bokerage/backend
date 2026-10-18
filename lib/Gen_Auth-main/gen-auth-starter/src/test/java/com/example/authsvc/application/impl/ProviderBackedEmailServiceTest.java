package com.example.authsvc.application.impl;

import com.example.authsvc.infrastructure.email.provider.EmailProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProviderBackedEmailServiceTest {

    @Mock
    private EmailProvider emailProvider;

    @Test
    void sendPasswordResetLink_delegatesToActiveProvider() {
        ProviderBackedEmailService service = new ProviderBackedEmailService(emailProvider);

        service.sendPasswordResetLink("user@example.com", "https://app.example.com/reset?token=abc123");

        verify(emailProvider).sendPasswordResetLink("user@example.com", "https://app.example.com/reset?token=abc123");
    }

    @Test
    void sendSuperAdminBootstrapCredentials_delegatesToActiveProvider() {
        ProviderBackedEmailService service = new ProviderBackedEmailService(emailProvider);

        service.sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");

        verify(emailProvider).sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");
    }
}
