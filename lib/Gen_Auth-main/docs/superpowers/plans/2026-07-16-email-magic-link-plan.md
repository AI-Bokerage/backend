# Email + Magic-Link Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore email sending (SMTP + SES) and magic-link password reset into `gen-auth-starter`, both optional and off by default, so a client embedding the starter pays zero cost unless they explicitly opt in.

**Architecture:** Both subsystems register their beans through the starter's existing unconditional `@ComponentScan("com.example.authsvc")` (in `GenAuthAutoConfiguration`) — no new scan directive needed. Optionality is enforced per-class with `@ConditionalOnProperty`, the same mechanism `EmailProviderConfig` already uses to pick between SMTP and SES.

**Tech Stack:** Spring Boot 4.0.6 `spring-boot-starter-mail` (SMTP), AWS SDK v2 `software.amazon.awssdk:ses` (already have the BOM via the existing KMS dependency), Redis (magic-link token storage, no new DB table).

## Global Constraints

- Every new subsystem is **off by default**: `app.email.enabled=false`, `app.magic-link.enabled=false`. No required env var, no bean registered, no connection attempted unless explicitly turned on.
- `app.magic-link.enabled=true` REQUIRES `app.email.enabled=true` — fail fast at boot with a clear `IllegalStateException` message if magic-link is on but email isn't, since a magic-link with no delivery mechanism is a broken feature, not a partial one.
- Source of truth for restored code: git commit `8f2c5b7` (tag `archive/starter-library-slice-pretrim` covers its ancestry — safe from gc). Every restored file in this plan is adapted from that commit, not written from scratch.
- **Trim scope on restore** — the pre-trim `EmailService`/`EmailProvider` had 4 methods. This plan restores only `sendPasswordResetLink`. The other three are dropped or deferred:
  - `sendSuperAdminBootstrapCredentials` — belongs to the super-admin/impersonation slice (a separate plan). Do not add it here.
  - `sendTenantAdminWelcome`, `sendInvitationWelcome` — these referenced an ADM sibling-service invitation flow that doesn't exist in this generic service at all. Permanently dropped, same call as `ServiceTokenController` in the design spec — not restored by any future slice either.
- **Genericize leftover "CPMS" branding** — `SmtpEmailProvider`/`SesEmailProvider`'s subject lines and email bodies still say "Reset your CPMS password" and sign off "— CPMS Security Team", even though `EmailHtmlTemplate`'s actual rendered output already says "Gen_AUTH" (someone already generocized the template but not these provider classes). Fix both to generic wording as part of restoring them, not as a follow-up.
- No new Flyway migration in this plan — magic-link is entirely Redis-backed.
- Docker Compose gets a **Mailhog** service for manual verification (SMTP capture with a web UI), on non-default host ports, following this project's existing pattern (Postgres 5433, Redis 6380) to avoid colliding with anything already running on 1025/8025.

---

### Task 1: Build dependencies and activation properties

**Files:**
- Modify: `gen-auth-starter/build.gradle`

**Interfaces:**
- Produces: `spring-boot-starter-mail` and `software.amazon.awssdk:ses` on the starter's compile classpath, available to every later task in this plan.

- [ ] **Step 1: Add the mail and SES dependencies**

In `gen-auth-starter/build.gradle`, inside the existing `dependencies { ... }` block, add a new section after the existing AWS SDK block (which currently ends with `implementation 'software.amazon.awssdk:kms'`):

```groovy
    // ─────────────────────────────────────────────────────────────────────
    // Email (SMTP + SES) — optional, gated by app.email.enabled
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'software.amazon.awssdk:ses'
```

Place it immediately after the existing AWS SDK block (which already declares `implementation platform("software.amazon.awssdk:bom:${awsSdkVersion}")` — the SES module resolves its version from that same BOM, no new version property needed).

- [ ] **Step 2: Verify the build resolves**

Run: `./gradlew.bat :gen-auth-starter:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL` (no source changes yet, this step only confirms the new dependencies resolve).

- [ ] **Step 3: Commit**

```bash
git add gen-auth-starter/build.gradle
git commit -m "add spring-boot-starter-mail and AWS SES SDK module for optional email sending"
```

---

### Task 2: Restore email sending (SMTP + SES), trimmed and genericized

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MailProperties.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/EmailProvider.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProvider.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SesEmailProvider.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/template/EmailHtmlTemplate.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/config/EmailProviderConfig.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProviderTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ProviderBackedEmailServiceTest.java`

**Interfaces:**
- Consumes: `AwsProperties` (existing, `com.example.authsvc.config.properties.AwsProperties` — `getAccessKeyId()`, `getSecretAccessKey()`), the existing `authAsync` executor (`AsyncConfig`, already registered).
- Produces: `EmailService.sendPasswordResetLink(String toEmail, String resetUrl)` — this is the only method Task 3 (magic-link) depends on.

- [ ] **Step 1: Create `MailProperties`**

```java
package com.example.authsvc.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email delivery configuration, bound from the {@code mail.*} namespace.
 * Only read when {@code app.email.enabled=true} (see {@link
 * com.example.authsvc.infrastructure.email.config.EmailProviderConfig}).
 *
 * <p>The {@code provider} property selects the active transport backend:
 * <ul>
 *   <li>{@code smtp} (default) — Spring {@code JavaMailSender}; uses the standard
 *       {@code spring.mail.*} auto-configuration alongside this config.</li>
 *   <li>{@code ses} — AWS Simple Email Service via SDK v2 {@code SesClient}.</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "mail")
public class MailProperties {

    /**
     * Active email delivery backend.
     * Accepted values: {@code smtp} (default), {@code ses}.
     */
    private String provider = "smtp";

    /** SES-specific settings — only required when {@code provider=ses}. */
    private Ses ses = new Ses();

    @Data
    public static class Ses {
        /** AWS region for the SES client (e.g. {@code ap-south-1}). */
        private String region;

        /** Verified sender address; must be verified in SES before use. */
        private String fromAddress;
    }

    @PostConstruct
    public void validate() {
        String p = provider == null ? "smtp" : provider.toLowerCase().trim();
        switch (p) {
            case "smtp" -> {
                // SMTP host / credentials are validated by Spring Boot's own
                // MailAutoConfiguration; nothing extra to assert here.
            }
            case "ses" -> {
                if (ses.getRegion() == null || ses.getRegion().isBlank()) {
                    throw new IllegalStateException(
                            "mail.ses.region must not be blank when mail.provider=ses");
                }
                if (ses.getFromAddress() == null || ses.getFromAddress().isBlank()) {
                    throw new IllegalStateException(
                            "mail.ses.from-address must not be blank when mail.provider=ses");
                }
            }
            default -> throw new IllegalStateException(
                    "Unknown mail.provider '%s'; accepted values: smtp, ses".formatted(provider));
        }
    }
}
```

This `@ConfigurationProperties` class is picked up automatically by `GenAuthAutoConfiguration`'s existing `@ConfigurationPropertiesScan("com.example.authsvc")` — no new scan directive needed. Its `@PostConstruct validate()` only runs once Spring actually instantiates the bean; since it has no `@ConditionalOnProperty` of its own, it IS always instantiated (all `@ConfigurationProperties` classes are), but `validate()` only throws for a bad `mail.provider` value — a client with `app.email.enabled=false` who never sets `mail.provider` still gets the harmless `smtp` default and passes validation. This is intentional: keep this one small class unconditional and simple rather than adding a `@ConditionalOnProperty` that would then need its own null-safety story.

- [ ] **Step 2: Create the trimmed `EmailService` interface**

```java
package com.example.authsvc.application.service;

/**
 * Minimal abstraction for sending transactional emails.
 * Only the subset needed by the magic-link password-reset flow is declared here.
 */
public interface EmailService {

    /**
     * Send a password-reset magic link to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param resetUrl  full reset URL including the raw token as a query parameter
     */
    void sendPasswordResetLink(String toEmail, String resetUrl);
}
```

- [ ] **Step 3: Create the trimmed `EmailProvider` interface**

```java
package com.example.authsvc.infrastructure.email.provider;

/**
 * Low-level email delivery abstraction.
 *
 * <p>Implementations handle the transport details for a specific backend
 * (SMTP, AWS SES) without carrying any business logic. The active
 * implementation is selected at startup based on {@code mail.provider}:
 *
 * <ul>
 *   <li>{@code smtp} (default) — {@link SmtpEmailProvider}</li>
 *   <li>{@code ses}            — {@link SesEmailProvider}</li>
 * </ul>
 *
 * <p>Business services depend only on {@link com.example.authsvc.application.service.EmailService};
 * they never interact with an {@code EmailProvider} directly.
 */
public interface EmailProvider {

    /**
     * Send a password-reset magic link to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param resetUrl  full reset URL including the raw token
     */
    void sendPasswordResetLink(String toEmail, String resetUrl);
}
```

- [ ] **Step 4: Restore `EmailHtmlTemplate` (fix the stale javadoc, no functional change)**

Copy verbatim from `8f2c5b7:gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/template/EmailHtmlTemplate.java`, with one change to the class-level javadoc — replace:

```java
/**
 * Shared HTML layout for transactional emails.
 *
 * <p>Table-based layout with inline CSS for maximum email-client compatibility
 * (Outlook, Gmail, Apple Mail). All caller-supplied values are HTML-escaped.
 *
 * <p>Forked from CPMS-Platform's {@code libs/java-common} (io.cpms.common.email.EmailHtmlTemplate)
 * as part of genericizing this service — the "CPMS" brand text below is a placeholder
 * carried over from that fork; make it configurable when doing the real genericization pass.
 */
```

with:

```java
/**
 * Shared HTML layout for transactional emails.
 *
 * <p>Table-based layout with inline CSS for maximum email-client compatibility
 * (Outlook, Gmail, Apple Mail). All caller-supplied values are HTML-escaped.
 */
```

The rendered output already says "Gen_AUTH" (see the `%s` template body) — only the doc comment was stale, claiming a placeholder that had already been resolved. Everything else in the file (the `render()` method, `InfoRow` record, `buildInfoCard`/`buildCta`/`esc` helpers) is retained as-is since super-admin's bootstrap email (a later slice) will reuse the `InfoRow`-based rendering.

- [ ] **Step 5: Restore `SmtpEmailProvider`, trimmed to password-reset and genericized**

```java
package com.example.authsvc.infrastructure.email.provider;

import com.example.authsvc.infrastructure.email.template.EmailHtmlTemplate;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * {@link EmailProvider} that delivers transactional email via SMTP using
 * Spring's {@link JavaMailSender}.
 *
 * <p>Active when {@code mail.provider=smtp} (the default).
 * Created as a Spring bean by
 * {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig};
 * not registered as a {@code @Component} directly so that the config class
 * can conditionally instantiate either this or {@link SesEmailProvider}.
 *
 * <p>Sent as a multipart/alternative message: an HTML part built via
 * {@link EmailHtmlTemplate} plus a plain-text fallback for clients that
 * don't render HTML.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    static final String PASSWORD_RESET_SUBJECT = "Reset your password";

    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        log.info("email.send.started provider=smtp type=password_reset to=***");
        try {
            send(toEmail, PASSWORD_RESET_SUBJECT, buildResetHtml(resetUrl), buildBody(resetUrl));
            log.info("email.send.success provider=smtp type=password_reset");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=password_reset reason={}", e.getMessage());
            // Intentionally swallowed: the caller already returned 202.
        }
    }

    private String buildResetHtml(String resetUrl) {
        return EmailHtmlTemplate.render(
                "Security",
                "Reset your password",
                null,
                "You requested a password reset for your account. Click the button below to set a new password. This link is valid for 15 minutes.",
                null,
                "Reset Password",
                resetUrl,
                "If you did not request this, you can safely ignore this email. Your password will not be changed until you click the button above.");
    }

    private String buildBody(String resetUrl) {
        return """
                You requested a password reset for your account.

                Click the link below to set a new password (valid for 15 minutes):

                %s

                If you did not request this, you can safely ignore this email.
                Your password will not be changed until you click the link above.
                """.formatted(resetUrl);
    }

    private void send(String toEmail, String subject, String htmlBody, String textBody) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(textBody, htmlBody);
        mailSender.send(mimeMessage);
    }
}
```

- [ ] **Step 6: Restore `SesEmailProvider`, trimmed to password-reset and genericized**

```java
package com.example.authsvc.infrastructure.email.provider;

import com.example.authsvc.infrastructure.email.template.EmailHtmlTemplate;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * {@link EmailProvider} that delivers transactional email via AWS Simple Email Service.
 *
 * <p>Active when {@code mail.provider=ses}. Created as a Spring bean by
 * {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig}.
 *
 * <p>Credentials are resolved through the AWS SDK v2 default provider chain
 * (environment variables → EC2 instance profile → ECS task role).
 * Explicit credentials can optionally be supplied via
 * {@code aws.access-key-id} / {@code aws.secret-access-key} properties.
 */
@Slf4j
public class SesEmailProvider implements EmailProvider {

    private static final String PASSWORD_RESET_SUBJECT = SmtpEmailProvider.PASSWORD_RESET_SUBJECT;

    private final SesClient sesClient;
    private final String    fromAddress;

    public SesEmailProvider(SesClient sesClient, String fromAddress) {
        this.sesClient   = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        log.info("email.send.started provider=ses type=password_reset to=***");
        try {
            send(toEmail, PASSWORD_RESET_SUBJECT, buildResetHtml(resetUrl), buildBody(resetUrl));
            log.info("email.send.success provider=ses type=password_reset");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=password_reset reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
            // Intentionally swallowed: the caller already returned 202.
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=password_reset reason={}", e.getMessage());
            // Intentionally swallowed: the caller already returned 202.
        }
    }

    private String buildResetHtml(String resetUrl) {
        return EmailHtmlTemplate.render(
                "Security",
                "Reset your password",
                null,
                "You requested a password reset for your account. Click the button below to set a new password. This link is valid for 15 minutes.",
                null,
                "Reset Password",
                resetUrl,
                "If you did not request this, you can safely ignore this email. Your password will not be changed until you click the button above.");
    }

    private String buildBody(String resetUrl) {
        return """
                You requested a password reset for your account.

                Click the link below to set a new password (valid for 15 minutes):

                %s

                If you did not request this, you can safely ignore this email.
                Your password will not be changed until you click the link above.
                """.formatted(resetUrl);
    }

    private void send(String toEmail, String subject, String htmlBody, String textBody) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                .text(Content.builder().data(textBody).charset("UTF-8").build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
    }
}
```

- [ ] **Step 7: Restore `EmailProviderConfig`, gated by `app.email.enabled`**

```java
package com.example.authsvc.infrastructure.email.config;

import com.example.authsvc.config.properties.AwsProperties;
import com.example.authsvc.config.properties.MailProperties;
import com.example.authsvc.infrastructure.email.provider.EmailProvider;
import com.example.authsvc.infrastructure.email.provider.SesEmailProvider;
import com.example.authsvc.infrastructure.email.provider.SmtpEmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

/**
 * Creates the active {@link EmailProvider} bean based on {@code mail.provider}.
 *
 * <p>This entire configuration class is gated by {@code app.email.enabled=true} —
 * when absent or false, no email beans are registered at all, no SMTP/SES
 * connection is ever attempted, and no email credentials are demanded.
 *
 * <table>
 *   <tr><th>mail.provider</th><th>Bean created</th></tr>
 *   <tr><td>smtp (default)</td><td>{@link SmtpEmailProvider}</td></tr>
 *   <tr><td>ses</td><td>{@link SesEmailProvider} + {@link SesClient}</td></tr>
 * </table>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailProviderConfig {

    private final MailProperties  mailProperties;
    private final AwsProperties   awsProperties;

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
    public SesClient sesClient() {
        String region = mailProperties.getSes().getRegion();
        SesClientBuilder builder = SesClient.builder().region(Region.of(region));

        String accessKey = awsProperties.getAccessKeyId();
        String secretKey = awsProperties.getSecretAccessKey();
        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
            log.info("email.provider.ses ses.client.initialized region={} credentials=static", region);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("email.provider.ses ses.client.initialized region={} credentials=default-chain", region);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
    public EmailProvider sesEmailProvider(SesClient sesClient) {
        String from = mailProperties.getSes().getFromAddress();
        log.info("email.provider.ses initialized fromAddress={}", from);
        return new SesEmailProvider(sesClient, from);
    }

    @Bean
    @ConditionalOnProperty(name = "mail.provider", havingValue = "smtp", matchIfMissing = true)
    public EmailProvider smtpEmailProvider(JavaMailSender javaMailSender) {
        log.info("email.provider.smtp initialized");
        return new SmtpEmailProvider(javaMailSender);
    }
}
```

- [ ] **Step 8: Restore `ProviderBackedEmailService`, trimmed and gated**

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.infrastructure.email.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@link EmailService} implementation that delegates to the currently active
 * {@link EmailProvider} (SMTP or SES) without containing any transport logic.
 *
 * <p>Owns the {@link Async} boundary: email delivery is fire-and-forget on the
 * {@code authAsync} thread pool. Gated by {@code app.email.enabled=true} — the
 * same condition as {@link com.example.authsvc.infrastructure.email.config.EmailProviderConfig},
 * so this bean and its {@link EmailProvider} dependency are always registered
 * or always absent together.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProviderBackedEmailService implements EmailService {

    private final EmailProvider emailProvider;

    @Override
    @Async("authAsync")
    public void sendPasswordResetLink(String toEmail, String resetUrl) {
        emailProvider.sendPasswordResetLink(toEmail, resetUrl);
    }
}
```

- [ ] **Step 9: Write `SmtpEmailProviderTest`**

```java
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
}
```

- [ ] **Step 10: Write `ProviderBackedEmailServiceTest`**

```java
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
}
```

- [ ] **Step 11: Run the new tests**

Run: `./gradlew.bat :gen-auth-starter:test --tests "*SmtpEmailProviderTest" --tests "*ProviderBackedEmailServiceTest" --console=plain`
Expected: both test classes pass, 0 failures.

- [ ] **Step 12: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MailProperties.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/ \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java \
        gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/email/ \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ProviderBackedEmailServiceTest.java
git commit -m "restore optional email sending (SMTP + SES), trimmed to password-reset and genericized"
```

---

### Task 3: Restore magic-link password reset, gated and cross-validated against email

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MagicLinkProperties.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/domain/model/MagicLinkEntry.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/domain/port/MagicLinkStore.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisMagicLinkStore.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/config/redis/RedisConfig.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/MagicLinkService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/MagicLinkServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/MagicLinkController.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkIssueRequest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkVerifyRequest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkIssueResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkVerifyResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/common/exception/MagicLinkInvalidException.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/MagicLinkActivationValidator.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/MagicLinkServiceImplTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisMagicLinkStoreTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/MagicLinkActivationValidatorTest.java`

**Interfaces:**
- Consumes: `EmailService.sendPasswordResetLink(String, String)` (Task 2), existing `AuthUserJpaRepository.findByEmailAndActiveTrue(String)`, `AuthSessionJpaRepository.deactivateAllByUserId(UUID, Instant)`, `RefreshTokenStore.revokeAllByUserId(UUID)`, `PasswordHasher.hash(String)`, `AuditLogService.log(AuditLogRequest)`, `RefreshTokenHashUtil.hash(String)`, `RateLimitExceededException` — all existing, unchanged.
- Produces: `POST /api/v1/auth/magic-link/issue`, `POST /api/v1/auth/magic-link/verify` — public endpoints, only registered when `app.magic-link.enabled=true`.

- [ ] **Step 1: Create `MagicLinkProperties`**

```java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the magic-link / forgot-password flow.
 * Only read when {@code app.magic-link.enabled=true}.
 *
 * <pre>
 * auth:
 *   magic-link:
 *     ttl: 15m
 *     frontend-reset-url: https://app.example.com/reset-password
 *     rate-limit-max-requests: 3
 *     rate-limit-window: 1h
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "auth.magic-link")
public class MagicLinkProperties {

    /** How long a magic-link token stays valid in Redis. */
    private Duration ttl = Duration.ofMinutes(15);

    /** Frontend URL that accepts ?token=<rawToken>. */
    private String frontendResetUrl = "http://localhost:3000/reset-password";

    /** Maximum issue attempts per (IP + email) within the rate-limit window. */
    private int rateLimitMaxRequests = 3;

    /** Sliding window for rate-limit counters. */
    private Duration rateLimitWindow = Duration.ofHours(1);
}
```

- [ ] **Step 2: Create `MagicLinkEntry`**

```java
package com.example.authsvc.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The value stored in Redis for an active magic-link / password-reset token.
 * Only the SHA-256 hash of the raw token is used as the Redis key — this
 * record is never stored directly; its JSON representation is the value.
 */
public record MagicLinkEntry(
        UUID    userId,
        UUID    tenantId,
        String  purpose,
        Instant expiresAt
) {
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
}
```

- [ ] **Step 3: Create the `MagicLinkStore` port**

```java
package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.MagicLinkEntry;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for storing/retrieving one-time magic-link entries.
 * Backed by Redis — no DB persistence for these tokens.
 */
public interface MagicLinkStore {

    /**
     * Persist a magic-link entry keyed by the SHA-256 hash of the raw token.
     *
     * @param tokenHash SHA-256 hex of the raw token (used as Redis key discriminator)
     * @param entry     the entry to store
     * @param ttl       how long the entry should live
     */
    void save(String tokenHash, MagicLinkEntry entry, Duration ttl);

    /**
     * Look up an entry by the SHA-256 hash of the submitted token.
     */
    Optional<MagicLinkEntry> find(String tokenHash);

    /**
     * Delete the entry immediately (one-time use enforcement).
     */
    void delete(String tokenHash);

    /**
     * Increment the issue-attempt counter for the given key and return the new count.
     * Counter is initialised with the given TTL on first call within the window.
     */
    long incrementRateCounter(String counterKey, Duration window);
}
```

- [ ] **Step 4: Create `RedisMagicLinkStore`**

```java
package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed {@link MagicLinkStore}.
 *
 * <p>Key schema:
 * <pre>
 *   auth:magic:{tokenHash}          →  MagicLinkEntry JSON  (TTL = configured ttl)
 *   auth:rate:magic:issue:{key}     →  integer counter      (TTL = rate-limit window)
 * </pre>
 *
 * <p>Only the SHA-256 hash of the raw token is ever stored as a Redis key.
 * The raw token is sent to the user by email and never persisted.
 *
 * <p>Registered as a {@code @Bean} in
 * {@link com.example.authsvc.config.redis.RedisConfig} — not annotated with
 * {@code @Component} — so the ObjectMapper is constructed with explicit
 * JavaTimeModule / ISO-8601 settings, consistent with
 * {@link com.example.authsvc.infrastructure.cache.RedisRefreshTokenStore}.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMagicLinkStore implements MagicLinkStore {

    static final String MAGIC_KEY_PREFIX  = "auth:magic:";
    static final String RATE_LIMIT_PREFIX = "auth:rate:magic:issue:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    public void save(String tokenHash, MagicLinkEntry entry, Duration ttl) {
        String key = MAGIC_KEY_PREFIX + tokenHash;
        String json;
        try {
            json = objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            log.error("magic_link.redis.serialize_failed userId={} reason={}", entry.userId(), e.getMessage());
            throw new IllegalStateException("Failed to serialize MagicLinkEntry", e);
        }
        redisTemplate.opsForValue().set(key, json, ttl);
        log.debug("magic_link.redis.stored userId={} ttlSeconds={}", entry.userId(), ttl.toSeconds());
    }

    @Override
    public Optional<MagicLinkEntry> find(String tokenHash) {
        String json = redisTemplate.opsForValue().get(MAGIC_KEY_PREFIX + tokenHash);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MagicLinkEntry.class));
        } catch (JsonProcessingException e) {
            log.error("magic_link.redis.deserialize_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String tokenHash) {
        redisTemplate.delete(MAGIC_KEY_PREFIX + tokenHash);
        log.debug("magic_link.redis.deleted");
    }

    @Override
    public long incrementRateCounter(String counterKey, Duration window) {
        String key   = RATE_LIMIT_PREFIX + counterKey;
        Long   count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 1L : count;
    }
}
```

- [ ] **Step 5: Register `RedisMagicLinkStore` as a conditional bean in `RedisConfig`**

Modify `gen-auth-starter/src/main/java/com/example/authsvc/config/redis/RedisConfig.java`. Add these imports:

```java
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.cache.RedisMagicLinkStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```

Add this method to the class, after the existing `refreshTokenStore` bean method:

```java
    /**
     * Only registered when {@code app.magic-link.enabled=true}. Reuses the
     * same JSON ObjectMapper configuration as {@link #refreshTokenStore}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
    public MagicLinkStore magicLinkStore(StringRedisTemplate stringRedisTemplate) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new RedisMagicLinkStore(stringRedisTemplate, mapper);
    }
```

- [ ] **Step 6: Create `MagicLinkService` interface**

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;

public interface MagicLinkService {

    MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip);

    MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request);
}
```

- [ ] **Step 7: Create `MagicLinkServiceImpl`, gated by `app.magic-link.enabled`**

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.MagicLinkService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Implements the forgot-password / magic-link reset flow.
 * Gated by {@code app.magic-link.enabled=true} (validated at boot against
 * {@code app.email.enabled} by {@link com.example.authsvc.config.MagicLinkActivationValidator}).
 *
 * <h3>Issue flow</h3>
 * <ol>
 *   <li>Rate-limit check (Redis counter keyed by IP + email hash)</li>
 *   <li>Lookup user — always return generic 202 to prevent account enumeration</li>
 *   <li>Generate 48-byte cryptographically random token (Base64-URL encoded)</li>
 *   <li>Store SHA-256 hash in Redis with configured TTL</li>
 *   <li>Send email asynchronously (fire-and-forget)</li>
 * </ol>
 *
 * <h3>Verify flow</h3>
 * <ol>
 *   <li>Hash incoming token, look up in Redis</li>
 *   <li>Validate expiry; delete entry immediately (one-time use)</li>
 *   <li>Hash new password using existing Argon2 hasher; update user record</li>
 *   <li>Revoke all active sessions and refresh tokens for user</li>
 *   <li>Write audit log entry</li>
 * </ol>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MagicLinkServiceImpl implements MagicLinkService {

    private static final SecureRandom   SECURE_RANDOM = new SecureRandom();
    private static final int            TOKEN_BYTES   = 48; // 384 bits
    private static final Base64.Encoder URL_ENCODER   = Base64.getUrlEncoder().withoutPadding();

    private final MagicLinkProperties      props;
    private final MagicLinkStore           magicLinkStore;
    private final RefreshTokenStore        refreshTokenStore;
    private final AuthUserJpaRepository    userRepo;
    private final AuthSessionJpaRepository sessionRepo;
    private final PasswordHasher           passwordHasher;
    private final AuditLogService          auditLogService;
    private final EmailService             emailService;

    @Override
    public MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip) {

        String normalizedEmail = request.email().trim().toLowerCase();

        String rateLimitKey = ip + ":" + RefreshTokenHashUtil.hash(normalizedEmail);
        long attempts = magicLinkStore.incrementRateCounter(rateLimitKey, props.getRateLimitWindow());
        if (attempts > props.getRateLimitMaxRequests()) {
            log.warn("magic_link.rate_limited ip={} attempts={}", ip, attempts);
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }

        var userOpt = userRepo.findByEmailAndActiveTrue(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.info("magic_link.ignored_unknown_email");
            return MagicLinkIssueResponse.generic();
        }

        var user = userOpt.get();

        byte[] rawBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawToken  = URL_ENCODER.encodeToString(rawBytes);
        String tokenHash = RefreshTokenHashUtil.hash(rawToken);

        Instant expiresAt = Instant.now().plus(props.getTtl());
        MagicLinkEntry entry = new MagicLinkEntry(
                user.getId(),
                user.getTenantId(),
                MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                expiresAt
        );
        magicLinkStore.save(tokenHash, entry, props.getTtl());
        log.info("magic_link.issued userId={}", user.getId());

        String baseUrl = buildResetBaseUrl(props.getFrontendResetUrl(), request.tenantSlug());
        String resetUrl = baseUrl + "?token=" + rawToken;
        emailService.sendPasswordResetLink(normalizedEmail, resetUrl);

        return MagicLinkIssueResponse.generic();
    }

    private static String buildResetBaseUrl(String configuredUrl, String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return configuredUrl;
        }
        try {
            URI uri = new URI(configuredUrl);
            String host = tenantSlug + "." + uri.getHost();
            int port = uri.getPort();
            String authority = port > 0 ? host + ":" + port : host;
            return uri.getScheme() + "://" + authority + uri.getPath();
        } catch (Exception e) {
            log.warn("magic_link.reset_url_build_failed url={} slug={}", configuredUrl, tenantSlug, e);
            return configuredUrl;
        }
    }

    @Override
    @Transactional
    public MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request) {

        String tokenHash = RefreshTokenHashUtil.hash(request.token());
        var entryOpt = magicLinkStore.find(tokenHash);

        if (entryOpt.isEmpty()) {
            log.info("magic_link.invalid");
            throw new MagicLinkInvalidException();
        }

        MagicLinkEntry entry = entryOpt.get();

        if (Instant.now().isAfter(entry.expiresAt())) {
            magicLinkStore.delete(tokenHash);
            log.info("magic_link.expired userId={}", entry.userId());
            throw new MagicLinkInvalidException();
        }

        var user = userRepo.findById(entry.userId())
                .filter(u -> u.isActive())
                .orElseThrow(() -> {
                    magicLinkStore.delete(tokenHash);
                    log.info("magic_link.user_not_found_or_inactive userId={}", entry.userId());
                    return new MagicLinkInvalidException();
                });

        magicLinkStore.delete(tokenHash);

        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        userRepo.save(user);

        UUID userId = user.getId();
        sessionRepo.deactivateAllByUserId(userId, Instant.now());
        refreshTokenStore.revokeAllByUserId(userId);

        auditLogService.log(new AuditLogRequest(
                user.getTenantId(),
                userId,
                "password.reset.success",
                null,
                null,
                "Password reset via magic link"
        ));

        log.info("password.reset.success userId={}", userId);
        return MagicLinkVerifyResponse.success();
    }
}
```

- [ ] **Step 8: Create `MagicLinkController`, gated by `app.magic-link.enabled`**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.MagicLinkService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles the forgot-password / magic-link reset flow. Only registered when
 * {@code app.magic-link.enabled=true}.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/magic-link/issue}  — requests a password-reset link</li>
 *   <li>{@code POST /api/v1/auth/magic-link/verify} — validates the token and resets password</li>
 * </ul>
 *
 * Both endpoints are public (no JWT required) — permitted in
 * {@code SecurityConfig}'s permit-list (see Task 3, Step 12 in this plan).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/magic-link")
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MagicLinkController {

    private final MagicLinkService  magicLinkService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/issue")
    public ResponseEntity<MagicLinkIssueResponse> issue(
            @Valid @RequestBody MagicLinkIssueRequest request,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();
        log.debug("magic_link.issue.request ip={}", ip);

        MagicLinkIssueResponse response = magicLinkService.issue(request, ip);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<MagicLinkVerifyResponse> verify(
            @Valid @RequestBody MagicLinkVerifyRequest request,
            HttpServletResponse httpResponse) {

        MagicLinkVerifyResponse response = magicLinkService.verify(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .body(response);
    }
}
```

- [ ] **Step 9: Create the 4 magic-link DTOs**

`gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkIssueRequest.java`:

```java
package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MagicLinkIssueRequest(
        @NotBlank @Email String email,
        /** Optional tenant slug — when present the reset link uses {slug}.{root} instead of root. */
        String tenantSlug
) {}
```

`gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkVerifyRequest.java`:

```java
package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.PasswordConfirmationRequest;
import com.example.authsvc.common.validation.PasswordsMatch;
import com.example.authsvc.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

@PasswordsMatch
public record MagicLinkVerifyRequest(
        @NotBlank String token,

        @ValidPassword
        String newPassword,

        @NotBlank String confirmPassword
) implements PasswordConfirmationRequest {}
```

`gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkIssueResponse.java`:

```java
package com.example.authsvc.api.dto.response;

public record MagicLinkIssueResponse(String message) {

    public static MagicLinkIssueResponse generic() {
        return new MagicLinkIssueResponse(
                "If the account exists, a reset link has been sent.");
    }
}
```

`gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkVerifyResponse.java`:

```java
package com.example.authsvc.api.dto.response;

public record MagicLinkVerifyResponse(String message) {

    public static MagicLinkVerifyResponse success() {
        return new MagicLinkVerifyResponse("Password reset successful. Please login again.");
    }
}
```

- [ ] **Step 10: Create `MagicLinkInvalidException`**

```java
package com.example.authsvc.common.exception;

/** Thrown when a magic-link token is missing, invalid, or expired. */
public class MagicLinkInvalidException extends RuntimeException {
    public MagicLinkInvalidException() {
        super("Invalid or expired reset link");
    }
}
```

- [ ] **Step 11: Restore the `GlobalExceptionHandler` handler for `MagicLinkInvalidException`**

Modify `gen-auth-starter/src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`. Add the import:

```java
import com.example.authsvc.common.exception.MagicLinkInvalidException;
```

Add this handler method, placed after the existing `handleEmailAlreadyExists` method:

```java
    @ExceptionHandler(MagicLinkInvalidException.class)
    public ResponseEntity<ErrorResponse> handleMagicLinkInvalid(MagicLinkInvalidException ex,
                                                                 HttpServletRequest request) {
        log.info("magic_link.invalid path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }
```

- [ ] **Step 12: Add `/api/v1/auth/magic-link/**` to `SecurityConfig`'s public permit-list**

Read `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java` first to find the existing `.requestMatchers(...).permitAll()` block (it currently lists `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `.well-known/jwks.json`, etc.). Add `"/api/v1/auth/magic-link/**"` to that same list of permitted patterns — magic-link issue/verify must work for unauthenticated users, same as login/register.

- [ ] **Step 13: Create `MagicLinkActivationValidator` — fail-fast cross-dependency check**

```java
package com.example.authsvc.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails startup fast if magic-link is enabled without email also being enabled —
 * a magic-link with no way to deliver the reset URL is a broken feature, not a
 * partial one. Only instantiated when {@code app.magic-link.enabled=true}; if
 * email is also enabled the check passes silently.
 */
@Component
@ConditionalOnProperty(prefix = "app.magic-link", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MagicLinkActivationValidator {

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @PostConstruct
    public void validate() {
        if (!emailEnabled) {
            throw new IllegalStateException(
                    "app.magic-link.enabled=true requires app.email.enabled=true — " +
                    "magic-link has no way to deliver the reset URL without email sending turned on.");
        }
    }
}
```

- [ ] **Step 14: Write `MagicLinkServiceImplTest`**

Read the existing `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/RegisterServiceImplTest.java` first for this project's established Mockito test conventions (constructor mocking style, `@ExtendWith(MockitoExtension.class)`), then write:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MagicLinkServiceImplTest {

    @Mock private MagicLinkProperties      props;
    @Mock private MagicLinkStore           magicLinkStore;
    @Mock private RefreshTokenStore        refreshTokenStore;
    @Mock private AuthUserJpaRepository    userRepo;
    @Mock private AuthSessionJpaRepository sessionRepo;
    @Mock private PasswordHasher           passwordHasher;
    @Mock private AuditLogService          auditLogService;
    @Mock private EmailService             emailService;

    @InjectMocks
    private MagicLinkServiceImpl service;

    @Test
    void issue_unknownEmail_returnsGenericResponseWithoutSendingEmail() {
        when(props.getRateLimitWindow()).thenReturn(Duration.ofHours(1));
        when(props.getRateLimitMaxRequests()).thenReturn(3);
        when(magicLinkStore.incrementRateCounter(anyString(), any())).thenReturn(1L);
        when(userRepo.findByEmailAndActiveTrue("unknown@example.com")).thenReturn(Optional.empty());

        MagicLinkIssueResponse response = service.issue(
                new MagicLinkIssueRequest("unknown@example.com", null), "127.0.0.1");

        assertThat(response.message()).isEqualTo("If the account exists, a reset link has been sent.");
        verify(emailService, org.mockito.Mockito.never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void issue_rateLimitExceeded_throws() {
        when(props.getRateLimitWindow()).thenReturn(Duration.ofHours(1));
        when(props.getRateLimitMaxRequests()).thenReturn(3);
        when(magicLinkStore.incrementRateCounter(anyString(), any())).thenReturn(4L);

        assertThatThrownBy(() -> service.issue(new MagicLinkIssueRequest("user@example.com", null), "127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void verify_invalidToken_throwsMagicLinkInvalid() {
        when(magicLinkStore.find(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(
                new MagicLinkVerifyRequest("bad-token", "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(MagicLinkInvalidException.class);
    }

    @Test
    void verify_expiredToken_deletesEntryAndThrows() {
        UUID userId = UUID.randomUUID();
        MagicLinkEntry expired = new MagicLinkEntry(
                userId, UUID.randomUUID(), MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                Instant.now().minusSeconds(60));
        when(magicLinkStore.find(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify(
                new MagicLinkVerifyRequest("expired-token", "NewPassw0rd!", "NewPassw0rd!")))
                .isInstanceOf(MagicLinkInvalidException.class);

        verify(magicLinkStore).delete(anyString());
    }

    @Test
    void verify_validToken_resetsPasswordAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        MagicLinkEntry entry = new MagicLinkEntry(
                userId, UUID.randomUUID(), MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                Instant.now().plusSeconds(600));
        when(magicLinkStore.find(anyString())).thenReturn(Optional.of(entry));

        AuthUserEntity user = new AuthUserEntity();
        user.setId(userId);
        user.setActive(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(anyString())).thenReturn("hashed");

        MagicLinkVerifyResponse response = service.verify(
                new MagicLinkVerifyRequest("valid-token", "NewPassw0rd!", "NewPassw0rd!"));

        assertThat(response.message()).isEqualTo("Password reset successful. Please login again.");
        verify(userRepo).save(user);
        verify(sessionRepo).deactivateAllByUserId(any(), any());
        verify(refreshTokenStore).revokeAllByUserId(userId);
        verify(magicLinkStore).delete(anyString());
    }
}
```

`AuthUserEntity` is `@Getter @Setter` Lombok (confirmed) — `setId`, `setActive(boolean)`, `isActive()`, `setPasswordHash`, `getTenantId`, `getId` all exist exactly as used in the test above; no adjustment needed.

- [ ] **Step 15: Write `RedisMagicLinkStoreTest`**

Read `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/` for an existing Redis store test (e.g. covering `RedisRefreshTokenStore`, if one exists) to match this project's Redis test conventions (likely embedded/mocked `StringRedisTemplate` or a Testcontainers Redis). Write `RedisMagicLinkStoreTest` covering: `save` then `find` round-trips the entry, `find` on a missing key returns empty, `delete` removes the key, `incrementRateCounter` sets a TTL only on the first call.

- [ ] **Step 16: Write `MagicLinkActivationValidatorTest`**

```java
package com.example.authsvc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicLinkActivationValidatorTest {

    @Test
    void validate_emailDisabled_throwsIllegalState() throws Exception {
        MagicLinkActivationValidator validator = new MagicLinkActivationValidator();
        var field = MagicLinkActivationValidator.class.getDeclaredField("emailEnabled");
        field.setAccessible(true);
        field.set(validator, false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.magic-link.enabled=true requires app.email.enabled=true");
    }

    @Test
    void validate_emailEnabled_doesNotThrow() throws Exception {
        MagicLinkActivationValidator validator = new MagicLinkActivationValidator();
        var field = MagicLinkActivationValidator.class.getDeclaredField("emailEnabled");
        field.setAccessible(true);
        field.set(validator, true);

        validator.validate(); // must not throw
    }
}
```

- [ ] **Step 17: Run the full starter test suite**

Run: `./gradlew.bat :gen-auth-starter:test --console=plain`
Expected: all tests pass, including the new ones from this task and Task 2.

- [ ] **Step 18: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MagicLinkProperties.java \
        gen-auth-starter/src/main/java/com/example/authsvc/domain/model/MagicLinkEntry.java \
        gen-auth-starter/src/main/java/com/example/authsvc/domain/port/MagicLinkStore.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisMagicLinkStore.java \
        gen-auth-starter/src/main/java/com/example/authsvc/config/redis/RedisConfig.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/service/MagicLinkService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/MagicLinkServiceImpl.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/controller/MagicLinkController.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkIssueRequest.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkVerifyRequest.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkIssueResponse.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkVerifyResponse.java \
        gen-auth-starter/src/main/java/com/example/authsvc/common/exception/MagicLinkInvalidException.java \
        gen-auth-starter/src/main/java/com/example/authsvc/config/MagicLinkActivationValidator.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/MagicLinkServiceImplTest.java \
        gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisMagicLinkStoreTest.java \
        gen-auth-starter/src/test/java/com/example/authsvc/config/MagicLinkActivationValidatorTest.java
git commit -m "restore optional magic-link password reset, gated on app.magic-link.enabled + email"
```

---

### Task 4: Docker Compose Mailhog service, demo config, and manual verification

**Files:**
- Modify: `docker-compose.yml`
- Modify: `gen-auth-demo/src/main/resources/application.yaml`
- Modify: `README.md` (gen-auth-starter's environment variables / feature documentation)

**Interfaces:**
- Consumes: everything from Tasks 1-3.
- Produces: a documented, runnable manual-verification path for a developer testing this slice.

- [ ] **Step 1: Add a Mailhog service to `docker-compose.yml`**

```yaml
  mailhog:
    image: mailhog/mailhog:v1.0.1
    ports:
      - '1026:1025'   # SMTP
      - '8026:8025'   # Web UI — open http://localhost:8026 to see captured mail
```

Add this as a new top-level service alongside `postgres` and `redis`, following the same non-default-host-port pattern already used there.

- [ ] **Step 2: Add commented example config to `gen-auth-demo/src/main/resources/application.yaml`**

Add this block, disabled by default, documented inline:

```yaml
# ===================================================================
# EMAIL + MAGIC-LINK (optional — off by default)
# ===================================================================
# To try this locally: start Mailhog (docker compose up -d mailhog),
# flip both flags to true, then check http://localhost:8026 for captured mail.

app:
  email:
    enabled: false
  magic-link:
    enabled: false

mail:
  provider: smtp

spring:
  mail:
    host: localhost
    port: 1026
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

auth:
  magic-link:
    frontend-reset-url: http://localhost:3000/reset-password
```

Insert this block in the same location/style as the other optional-feature sections already documented in that file.

- [ ] **Step 3: Boot the demo with email + magic-link enabled and verify against Mailhog**

```bash
docker compose up -d mailhog
# Postgres/Redis already running from prior verification passes, or:
# docker compose up -d
```

Then boot with the two flags overridden true (env vars, matching this project's `bootRun` env-var convention):

```bash
APP_EMAIL_ENABLED=true APP_MAGIC_LINK_ENABLED=true \
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC \
AUTH_DB_URL=jdbc:postgresql://localhost:5433/genauth \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
SPRING_DATA_REDIS_PORT=6380 \
INTERNAL_SERVICE_SECRET=dev-secret \
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
JWT_ISSUER=genauth JWT_AUDIENCE=genauth \
./gradlew.bat :gen-auth-demo:bootRun
```

In another terminal, register a user then request a reset:

```bash
curl -i -X POST http://localhost:8101/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"reset-test@example.com","password":"correct horse battery staple"}'

curl -i -X POST http://localhost:8101/api/v1/auth/magic-link/issue \
  -H 'Content-Type: application/json' \
  -d '{"email":"reset-test@example.com"}'
# expect: 202, generic message
```

Open `http://localhost:8026` — confirm the reset email was actually captured, with the correct subject ("Reset your password") and a reset URL containing a token. Copy the token from the captured email's link and verify:

```bash
curl -i -X POST http://localhost:8101/api/v1/auth/magic-link/verify \
  -H 'Content-Type: application/json' \
  -d '{"token":"<paste-token-here>","newPassword":"AnotherPassw0rd!","confirmPassword":"AnotherPassw0rd!"}'
# expect: 200
```

Then confirm the OLD password no longer works and the NEW one does, via `/api/v1/auth/login`.

- [ ] **Step 4: Verify the off-by-default state boots clean**

Stop the demo, unset `APP_EMAIL_ENABLED`/`APP_MAGIC_LINK_ENABLED` (or explicitly set both to `false`), and reboot. Confirm:
- `curl -i http://localhost:8101/actuator/health` → `UP`
- `curl -i -X POST http://localhost:8101/api/v1/auth/magic-link/issue -d '{}'` → `404` (endpoint doesn't exist — bean was never registered, not a 403/disabled-feature response)
- No SMTP connection attempt appears in the boot log.

- [ ] **Step 5: Verify magic-link-without-email fails fast**

Boot once more with `APP_MAGIC_LINK_ENABLED=true` but `APP_EMAIL_ENABLED` unset/false. Confirm the application fails to start with the `MagicLinkActivationValidator`'s `IllegalStateException` message visible in the boot log/exit trace.

- [ ] **Step 6: Update `README.md`**

Add `app.email.enabled`, `app.magic-link.enabled`, `mail.provider` (and note `mail.ses.*` when `mail.provider=ses`) to the environment/config documentation, marked optional/off-by-default, with a one-line cross-dependency note (magic-link requires email). Add a line to the "Not done yet" or module-layout section noting email + magic-link are now restored (optional).

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml gen-auth-demo/src/main/resources/application.yaml README.md
git commit -m "add Mailhog for manual verification, document optional email/magic-link config"
```
