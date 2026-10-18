package com.example.authsvc.infrastructure.email.provider;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendPasswordResetLink_sendsMimeMessageWithResetUrl() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender);
        provider.sendPasswordResetLink("user@example.com", "https://app.example.com/reset?token=abc123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Reset your password");
    }

    @Test
    void sendPasswordResetLink_swallowsExceptionsAndDoesNotPropagate() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP connection refused"));

        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender);

        // Must not throw — caller already returned 202 to the client.
        provider.sendPasswordResetLink("user@example.com", "https://app.example.com/reset?token=abc123");
    }

    @Test
    void sendTenantAdminWelcome_sendsMimeMessageWithSubject() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender);
        provider.sendTenantAdminWelcome("admin@example.com", "Jane", "tempPass123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("admin@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Your admin account is ready");
    }

    @Test
    void sendInvitationWelcome_sendsMimeMessageWithSubject() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailProvider provider = new SmtpEmailProvider(mailSender);
        provider.sendInvitationWelcome("user@example.com", "John", "tempPass123", "https://app.example.com/login");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Your account is ready");
    }
}
