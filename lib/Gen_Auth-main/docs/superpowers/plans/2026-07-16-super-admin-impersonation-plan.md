# Super-Admin Login, Bootstrap, Password Reset, and Impersonation-Token Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore platform super-admin login, bootstrap-on-boot, magic-link password reset, and internal impersonation-token issuance into `gen-auth-starter`, all off by default, gated behind one `app.super-admin.enabled` flag.

**Architecture:** All new beans register through the starter's existing unconditional `@ComponentScan`/`@ConfigurationPropertiesScan`/`@EnableJpaRepositories` (in `GenAuthAutoConfiguration`) — no new scan directive needed. A single `app.super-admin.enabled` flag gates the whole bucket (login, bootstrap, magic-link reset, impersonation-token issuance) via `@ConditionalOnProperty`, plus a dedicated fail-fast validator enforcing its one hard cross-dependency: super-admin requires email (`app.email.enabled=true`) too, since both the bootstrap-credential email and the magic-link reset email need it. Impersonation-token issuance is re-homed onto the existing `/internal/**` + `X-Internal-Secret` mechanism (`InternalTokenAuthFilter`), not the deleted HMAC filter the original code used.

**Tech Stack:** Spring Boot 4.0.6 / Java 21, Flyway (starter's own independent instance), Redis (reused magic-link infrastructure from the email+magic-link slice), Postgres.

## Global Constraints

- Off by default: `app.super-admin.enabled=false`. No bean registered, no `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` demanded, no `platform_super_admin` row created unless explicitly turned on.
- **Hard cross-dependency**: `app.super-admin.enabled=true` REQUIRES `app.email.enabled=true` too — fail fast at boot (`IllegalStateException`, clear message) otherwise. Both the bootstrap-credential email and the magic-link password-reset email need it; there is no partial mode.
- Source of truth for restored code: git commit `8f2c5b7` (tag `archive/starter-library-slice-pretrim` covers its ancestry). Adapt, do not copy blind — several things changed since that commit was written (see below).
- **`ServiceTokenController` stays excluded** (decided in the design spec) — CPMS sibling-service coupling, not part of this restoration.
- **Impersonation-token issuance is re-homed, not restored verbatim.** The original `ImpersonationTokenController` lived at `POST /v1/impersonation-token`, guarded by `InternalHmacAuthFilter` — a class that no longer exists anywhere in this codebase (deleted, not deferred; confirmed via `git log --all` — it never survives past the trim commit). The original design also assumed an exclusive external caller ("SUP-SVC") that pre-resolves tenant slug and impersonation role before calling in. This plan drops that framing entirely: the endpoint moves to `POST /internal/auth/impersonation-token`, protected by the starter's existing `InternalTokenAuthFilter` (`X-Internal-Secret` header — the same mechanism already guarding `/internal/auth/users` and `/internal/auth/keys/**`). No new auth filter is written. Javadoc referencing "SUP-SVC"/"ADM-SVC" is genericized to describe any internal caller.
- **`DevDataSeeder` is NOT part of this restoration.** It seeds a `TENANT_USER` (not `platform_super_admin`), and its permission set (`pmt.access`, `billing.admin`, references to "ADM's RbacPublisherService") is CPMS-platform RBAC machinery this generic service doesn't have (per the existing README: "this service doesn't own a Role/Permission concept of its own"). It is unrelated to super-admin/impersonation despite living in the same `infrastructure.seed` package pre-trim. Not restored by this or any other slice.
- **Reuse `TenantConstants.PLATFORM_TENANT_ID`** (`gen-auth-starter/src/main/java/com/example/authsvc/domain/TenantConstants.java` — already exists, unchanged since the original genericization pass) instead of redeclaring a duplicate sentinel constant on `PlatformSuperAdminEntity`, which is what the pre-trim code did.
- **Genericize leftover "CPMS" branding** in every restored string (bootstrap email subject/body, any log message, any javadoc) — same rule as the email+magic-link slice.
- **No firstName/lastName fields** on `SuperAdminProperties` — the pre-trim version had them but its own javadoc says "not persisted to auth_users today"; dead fields, dropped (YAGNI).
- **No default email/password values** on `SuperAdminProperties` — the pre-trim version defaulted to `admin@cpms.local` / `ChangeMe123!`, a real security anti-pattern to ship as a library default. Required (non-blank) only when `app.super-admin.enabled=true`, enforced by the same fail-fast validator mentioned above.
- Reuse the email+magic-link slice's Redis-backed `MagicLinkStore`/`MagicLinkProperties`/`MagicLinkController`-adjacent DTOs (`MagicLinkIssueRequest`/`Response`, `MagicLinkVerifyRequest`/`Response`) and `InternalSessionRevocationService` — do not duplicate them.

---

### Task 1: Extend email sending with a super-admin bootstrap-credentials method

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/EmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SesEmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ProviderBackedEmailServiceTest.java` (extend existing)

**Interfaces:**
- Consumes: `EmailHtmlTemplate.render(...)` and `EmailHtmlTemplate.InfoRow` (both already exist, unchanged, kept specifically for this reuse — see the email+magic-link plan's Task 2 Step 4 comment).
- Produces: `EmailService.sendSuperAdminBootstrapCredentials(String email, String password)` — Task 3's `BootstrapSuperAdminInitializer` calls this.

- [ ] **Step 1: Add the method to `EmailService`**

Modify `EmailService.java` to:

```java
package com.example.authsvc.application.service;

/**
 * Minimal abstraction for sending transactional emails.
 */
public interface EmailService {

    /**
     * Send a password-reset magic link to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param resetUrl  full reset URL including the raw token as a query parameter
     */
    void sendPasswordResetLink(String toEmail, String resetUrl);

    /**
     * Send the bootstrap credentials for the platform super admin.
     *
     * @param email     recipient email address
     * @param password  plaintext bootstrap password from configuration
     */
    void sendSuperAdminBootstrapCredentials(String email, String password);
}
```

- [ ] **Step 2: Add the method to `EmailProvider`**

Modify `EmailProvider.java` to:

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

    /**
     * Send the bootstrap credentials for the platform super admin.
     *
     * @param email     recipient email address
     * @param password  plaintext bootstrap password from configuration
     */
    void sendSuperAdminBootstrapCredentials(String email, String password);
}
```

- [ ] **Step 3: Implement it in `SmtpEmailProvider`**

Add these members to `SmtpEmailProvider.java` (alongside the existing `PASSWORD_RESET_SUBJECT` constant and `sendPasswordResetLink` method — do not remove anything already there):

```java
    static final String SUPER_ADMIN_BOOTSTRAP_SUBJECT = "Your super admin account is ready";

    @Override
    public void sendSuperAdminBootstrapCredentials(String email, String password) {
        log.info("email.send.started provider=smtp type=superadmin_bootstrap to=***");
        try {
            send(email, SUPER_ADMIN_BOOTSTRAP_SUBJECT,
                    buildBootstrapHtml(email, password), buildBootstrapBody(email, password));
            log.info("email.send.success provider=smtp type=superadmin_bootstrap");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=superadmin_bootstrap reason={}", e.getMessage());
        }
    }

    private String buildBootstrapHtml(String email, String password) {
        return EmailHtmlTemplate.render(
                "Account",
                "Your super admin account is ready",
                null,
                "A platform super admin account has been created. Use the credentials below to log in.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Email", email),
                        new EmailHtmlTemplate.InfoRow("Temporary Password", password)),
                null,
                null,
                "Please log in and change your password immediately. This email is sent only when the super admin account is created or its email is reassigned.");
    }

    private String buildBootstrapBody(String email, String password) {
        return """
                A platform super admin account is ready.

                Email: %s

                Temporary Password: %s

                Please log in and change your password immediately.

                This email is sent only when the super admin account is created or its email is reassigned.
                """.formatted(email, password);
    }
```

- [ ] **Step 4: Implement it in `SesEmailProvider`**

Add the same members (adapted to SES's `send()` helper) to `SesEmailProvider.java`:

```java
    private static final String SUPER_ADMIN_BOOTSTRAP_SUBJECT = SmtpEmailProvider.SUPER_ADMIN_BOOTSTRAP_SUBJECT;

    @Override
    public void sendSuperAdminBootstrapCredentials(String email, String password) {
        log.info("email.send.started provider=ses type=superadmin_bootstrap to=***");
        try {
            send(email, SUPER_ADMIN_BOOTSTRAP_SUBJECT,
                    buildBootstrapHtml(email, password), buildBootstrapBody(email, password));
            log.info("email.send.success provider=ses type=superadmin_bootstrap");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=superadmin_bootstrap reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=superadmin_bootstrap reason={}", e.getMessage());
        }
    }

    private String buildBootstrapHtml(String email, String password) {
        return EmailHtmlTemplate.render(
                "Account",
                "Your super admin account is ready",
                null,
                "A platform super admin account has been created. Use the credentials below to log in.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Email", email),
                        new EmailHtmlTemplate.InfoRow("Temporary Password", password)),
                null,
                null,
                "Please log in and change your password immediately. This email is sent only when the super admin account is created or its email is reassigned.");
    }

    private String buildBootstrapBody(String email, String password) {
        return """
                A platform super admin account is ready.

                Email: %s

                Temporary Password: %s

                Please log in and change your password immediately.

                This email is sent only when the super admin account is created or its email is reassigned.
                """.formatted(email, password);
    }
```

- [ ] **Step 5: Implement it in `ProviderBackedEmailService`**

Add to `ProviderBackedEmailService.java` (alongside the existing `sendPasswordResetLink`):

```java
    @Override
    @Async("authAsync")
    public void sendSuperAdminBootstrapCredentials(String email, String password) {
        try {
            emailProvider.sendSuperAdminBootstrapCredentials(email, password);
            log.info("superadmin.bootstrap.email.sent email={}", email);
        } catch (Exception e) {
            log.error("superadmin.bootstrap.email.failed email={} reason={}", email, e.getMessage(), e);
        }
    }
```

- [ ] **Step 6: Extend `ProviderBackedEmailServiceTest`**

Add this test method to the existing test class:

```java
    @Test
    void sendSuperAdminBootstrapCredentials_delegatesToActiveProvider() {
        ProviderBackedEmailService service = new ProviderBackedEmailService(emailProvider);

        service.sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");

        verify(emailProvider).sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");
    }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :gen-auth-starter:test --tests "*ProviderBackedEmailServiceTest" --console=plain`
Expected: pass, 3 tests (2 existing + 1 new).

Then run the full suite once: `./gradlew.bat :gen-auth-starter:test --console=plain` → expect no regressions.

- [ ] **Step 8: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/EmailProvider.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProvider.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SesEmailProvider.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ProviderBackedEmailServiceTest.java
git commit -m "extend EmailService with super-admin bootstrap credentials email, genericized"
```

---

### Task 2: Platform super-admin persistence (migration, entity, repository, properties)

**Files:**
- Create: `gen-auth-starter/src/main/resources/db/migration/genauth/V4__platform_super_admin.sql`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/PlatformSuperAdminEntity.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/PlatformSuperAdminJpaRepository.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/SuperAdminProperties.java`

**Interfaces:**
- Produces: `PlatformSuperAdminEntity` (JPA entity), `PlatformSuperAdminJpaRepository.findByEmailAndActiveTrue(String)` / `.findById(UUID)` (standard `JpaRepository` methods), `SuperAdminProperties.getEmail()`/`.getPassword()` — Task 3's `BootstrapSuperAdminInitializer` and Task 4's `SuperAdminLoginServiceImpl` consume all of these.

- [ ] **Step 1: Write the Flyway migration**

```sql
-- V4__platform_super_admin.sql
--
-- Stores exactly one platform-level super admin account, intentionally
-- separate from auth_users so platform credentials are never co-mingled
-- with tenant user data. Runs unconditionally through the starter's own
-- Flyway instance regardless of app.super-admin.enabled — an unused empty
-- table costs nothing, matching how jwt_signing_key exists even in kms mode.

CREATE TABLE IF NOT EXISTS platform_super_admin (
    id            UUID                     PRIMARY KEY,
    email         VARCHAR(255)             NOT NULL UNIQUE,
    password_hash VARCHAR(255)             NOT NULL,
    active        BOOLEAN                  NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Create `PlatformSuperAdminEntity`**

```java
package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code platform_super_admin} table.
 *
 * <p>Stores exactly one platform-level super admin account, bootstrapped on
 * first startup by
 * {@link com.example.authsvc.infrastructure.seed.BootstrapSuperAdminInitializer}
 * when {@code app.super-admin.enabled=true}. Intentionally separate from
 * {@code auth_users} so platform credentials are never co-mingled with tenant
 * user data.
 *
 * <p>Its tenant context in JWTs uses the shared
 * {@link com.example.authsvc.domain.TenantConstants#PLATFORM_TENANT_ID} sentinel
 * — not a locally-declared constant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_super_admin")
public class PlatformSuperAdminEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 3: Create `PlatformSuperAdminJpaRepository`**

```java
package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for the {@code platform_super_admin} table.
 * Only one active row is expected at any time.
 */
@Repository
public interface PlatformSuperAdminJpaRepository extends JpaRepository<PlatformSuperAdminEntity, UUID> {

    Optional<PlatformSuperAdminEntity> findByEmailAndActiveTrue(String email);
}
```

- [ ] **Step 4: Create `SuperAdminProperties`**

```java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap super admin credentials, bound from the {@code auth.super-admin.*}
 * namespace. Only meaningful when {@code app.super-admin.enabled=true} — see
 * {@link com.example.authsvc.config.SuperAdminActivationValidator} for the
 * fail-fast checks that enforce both are set correctly together.
 *
 * <p>No default email/password — both are required (non-blank) once the
 * feature is enabled; shipping a real default password would be a security
 * anti-pattern for a library.
 *
 * <pre>
 *   SUPER_ADMIN_EMAIL    — env var override
 *   SUPER_ADMIN_PASSWORD — env var override; change after first login
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "auth.super-admin")
public class SuperAdminProperties {

    /** Email address of the bootstrap super admin. Must be unique in platform_super_admin. */
    private String email;

    /** Plain-text initial password — encoded by {@code PasswordHasher} before persistence. */
    private String password;
}
```

- [ ] **Step 5: Verify the build compiles (no behavior yet — nothing conditional wired until Task 3)**

Run: `./gradlew.bat :gen-auth-starter:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/resources/db/migration/genauth/V4__platform_super_admin.sql \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/PlatformSuperAdminEntity.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/PlatformSuperAdminJpaRepository.java \
        gen-auth-starter/src/main/java/com/example/authsvc/config/properties/SuperAdminProperties.java
git commit -m "add platform_super_admin persistence: migration, entity, repository, properties"
```

---

### Task 3: Activation validator and bootstrap-on-boot initializer

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/SuperAdminActivationValidator.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/seed/BootstrapSuperAdminInitializer.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/SuperAdminActivationValidatorTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/seed/BootstrapSuperAdminInitializerTest.java`

**Interfaces:**
- Consumes: `SuperAdminProperties` (Task 2), `PlatformSuperAdminJpaRepository` (Task 2), `PasswordHasher.hash(CharSequence)` (existing, unchanged), `EmailService.sendSuperAdminBootstrapCredentials(String, String)` (Task 1).
- Produces: a guaranteed-non-blank `SuperAdminProperties` state by the time `BootstrapSuperAdminInitializer` runs (the validator throws first otherwise — see Step 1 for why there's no bean-ordering race here, unlike the magic-link slice's `MagicLinkActivationValidator`).

- [ ] **Step 1: Create `SuperAdminActivationValidator`**

```java
package com.example.authsvc.config;

import com.example.authsvc.config.properties.SuperAdminProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails startup fast if the super-admin bucket (login, bootstrap, magic-link
 * reset, impersonation-token issuance) is enabled without its two hard
 * requirements both being satisfied:
 * <ol>
 *   <li>{@code app.email.enabled=true} — bootstrap-credential and password-reset
 *       emails both need it.</li>
 *   <li>{@code auth.super-admin.email} / {@code auth.super-admin.password} both
 *       set (non-blank).</li>
 * </ol>
 *
 * <p>Only instantiated when {@code app.super-admin.enabled=true}. Unlike
 * {@code MagicLinkActivationValidator} in the email+magic-link slice, there is
 * no missing-bean race here to worry about: every bean this bucket depends on
 * ({@link SuperAdminProperties}, {@code PlatformSuperAdminJpaRepository},
 * {@code PasswordHasher}) is always registered regardless of configuration —
 * the only failure modes are blank string values, which this validator's own
 * {@code @PostConstruct} catches directly and reliably.
 */
@Component
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminActivationValidator {

    private final SuperAdminProperties superAdminProperties;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @PostConstruct
    public void validate() {
        if (!emailEnabled) {
            throw new IllegalStateException(
                    "app.super-admin.enabled=true requires app.email.enabled=true — " +
                    "bootstrap-credential and password-reset emails cannot be delivered without it.");
        }
        if (superAdminProperties.getEmail() == null || superAdminProperties.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "auth.super-admin.email must not be blank when app.super-admin.enabled=true");
        }
        if (superAdminProperties.getPassword() == null || superAdminProperties.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "auth.super-admin.password must not be blank when app.super-admin.enabled=true");
        }
    }
}
```

- [ ] **Step 2: Create `BootstrapSuperAdminInitializer`**

```java
package com.example.authsvc.infrastructure.seed;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.config.properties.SuperAdminProperties;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Ensures a bootstrap super admin account exists every time the application
 * starts, when {@code app.super-admin.enabled=true}.
 *
 * <h3>Idempotency</h3>
 * <ol>
 *   <li>Looks up the well-known super-admin ID.</li>
 *   <li>If found with a matching email: logs and exits — the record is never modified.</li>
 *   <li>If found with a different email: updates the record and re-sends the bootstrap email.</li>
 *   <li>If absent: creates the account, encodes the password, sends the bootstrap email.</li>
 * </ol>
 *
 * <h3>Failure behaviour</h3>
 * Any unexpected exception propagates out of {@link #run}, aborting startup —
 * this must never run in a partially initialised state.
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapSuperAdminInitializer implements ApplicationRunner {

    /**
     * Well-known, stable UUID for the platform super admin user. A fixed ID
     * guarantees the record is identical across environments.
     */
    static final UUID SUPER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SuperAdminProperties            superAdminProperties;
    private final PlatformSuperAdminJpaRepository superAdminRepo;
    private final PasswordHasher                  passwordHasher;
    private final EmailService                    emailService;

    @Override
    public void run(ApplicationArguments args) {
        String email = superAdminProperties.getEmail();
        log.info("superadmin.bootstrap.started email={}", email);

        try {
            Optional<PlatformSuperAdminEntity> existing = superAdminRepo.findById(SUPER_ADMIN_ID);

            if (existing.isPresent()) {
                PlatformSuperAdminEntity entity = existing.get();
                if (entity.getEmail().equals(email)) {
                    log.info("superadmin.bootstrap.exists email={}", email);
                } else {
                    String oldEmail = entity.getEmail();
                    entity.setEmail(email);
                    entity.setPasswordHash(passwordHasher.hash(superAdminProperties.getPassword()));
                    superAdminRepo.save(entity);
                    log.info("superadmin.bootstrap.updated oldEmail={} newEmail={}", oldEmail, email);
                    sendBootstrapEmail(email, superAdminProperties.getPassword(), "email_updated");
                }
                return;
            }

            PlatformSuperAdminEntity superAdmin = PlatformSuperAdminEntity.builder()
                    .id(SUPER_ADMIN_ID)
                    .email(email)
                    .passwordHash(passwordHasher.hash(superAdminProperties.getPassword()))
                    .active(true)
                    .build();

            superAdminRepo.save(superAdmin);
            log.info("superadmin.bootstrap.created email={}", email);
            sendBootstrapEmail(email, superAdminProperties.getPassword(), "created");

        } catch (Exception e) {
            log.error("superadmin.bootstrap.failed email={} reason={}", email, e.getMessage(), e);
            throw e;
        }
    }

    private void sendBootstrapEmail(String email, String password, String reason) {
        log.info("superadmin.bootstrap.email.sending email={} reason={}", email, reason);

        String previousReason = MDC.get("superadminBootstrapReason");
        try {
            MDC.put("superadminBootstrapReason", reason);
            emailService.sendSuperAdminBootstrapCredentials(email, password);
        } catch (Exception e) {
            log.error("superadmin.bootstrap.email.failed email={} reason={} error={}",
                    email, reason, e.getMessage(), e);
        } finally {
            if (previousReason != null) {
                MDC.put("superadminBootstrapReason", previousReason);
            } else {
                MDC.remove("superadminBootstrapReason");
            }
        }
    }
}
```

Note: `sendBootstrapEmail` can safely call `emailService.sendSuperAdminBootstrapCredentials` unconditionally — `SuperAdminActivationValidator` (Step 1) guarantees `app.email.enabled=true` whenever this initializer is even registered, so `EmailService` is always a real bean here, never absent.

- [ ] **Step 3: Write `SuperAdminActivationValidatorTest`**

```java
package com.example.authsvc.config;

import com.example.authsvc.config.properties.SuperAdminProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuperAdminActivationValidatorTest {

    @Test
    void validate_emailDisabled_throws() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("admin@example.com");
        props.setPassword("TempPass123!");
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.super-admin.enabled=true requires app.email.enabled=true");
    }

    @Test
    void validate_blankEmail_throws() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("");
        props.setPassword("TempPass123!");
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.super-admin.email must not be blank");
    }

    @Test
    void validate_blankPassword_throws() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("admin@example.com");
        props.setPassword(null);
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.super-admin.password must not be blank");
    }

    @Test
    void validate_allSet_doesNotThrow() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("admin@example.com");
        props.setPassword("TempPass123!");
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, true);

        validator.validate(); // must not throw
    }

    private static void setEmailEnabled(SuperAdminActivationValidator validator, boolean value) throws Exception {
        var field = SuperAdminActivationValidator.class.getDeclaredField("emailEnabled");
        field.setAccessible(true);
        field.set(validator, value);
    }
}
```

- [ ] **Step 4: Write `BootstrapSuperAdminInitializerTest`**

Read `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/MagicLinkServiceImplTest.java` first for this project's Mockito conventions, then write:

```java
package com.example.authsvc.infrastructure.seed;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.config.properties.SuperAdminProperties;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapSuperAdminInitializerTest {

    private static final UUID SUPER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private SuperAdminProperties            superAdminProperties;
    @Mock private PlatformSuperAdminJpaRepository superAdminRepo;
    @Mock private PasswordHasher                  passwordHasher;
    @Mock private EmailService                    emailService;

    @InjectMocks
    private BootstrapSuperAdminInitializer initializer;

    @Test
    void run_noExistingRecord_createsAccountAndSendsEmail() {
        when(superAdminProperties.getEmail()).thenReturn("admin@example.com");
        when(superAdminProperties.getPassword()).thenReturn("TempPass123!");
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenReturn("hashed");

        initializer.run(new DefaultApplicationArguments());

        verify(superAdminRepo).save(org.mockito.ArgumentMatchers.any(PlatformSuperAdminEntity.class));
        verify(emailService).sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");
    }

    @Test
    void run_existingRecordSameEmail_doesNothing() {
        when(superAdminProperties.getEmail()).thenReturn("admin@example.com");
        PlatformSuperAdminEntity existing = PlatformSuperAdminEntity.builder()
                .id(SUPER_ADMIN_ID).email("admin@example.com").passwordHash("hashed").active(true).build();
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(existing));

        initializer.run(new DefaultApplicationArguments());

        verify(superAdminRepo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailService, never()).sendSuperAdminBootstrapCredentials(anyString(), anyString());
    }

    @Test
    void run_existingRecordDifferentEmail_updatesAndResendsEmail() {
        when(superAdminProperties.getEmail()).thenReturn("new-admin@example.com");
        when(superAdminProperties.getPassword()).thenReturn("TempPass123!");
        PlatformSuperAdminEntity existing = PlatformSuperAdminEntity.builder()
                .id(SUPER_ADMIN_ID).email("old-admin@example.com").passwordHash("old-hash").active(true).build();
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(existing));
        when(passwordHasher.hash(anyString())).thenReturn("new-hash");

        initializer.run(new DefaultApplicationArguments());

        verify(superAdminRepo).save(existing);
        verify(emailService).sendSuperAdminBootstrapCredentials("new-admin@example.com", "TempPass123!");
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew.bat :gen-auth-starter:test --tests "*SuperAdminActivationValidatorTest" --tests "*BootstrapSuperAdminInitializerTest" --console=plain`
Expected: all pass (4 + 3 = 7 tests).

Then the full suite: `./gradlew.bat :gen-auth-starter:test --console=plain` → no regressions.

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/SuperAdminActivationValidator.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/seed/BootstrapSuperAdminInitializer.java \
        gen-auth-starter/src/test/java/com/example/authsvc/config/SuperAdminActivationValidatorTest.java \
        gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/seed/BootstrapSuperAdminInitializerTest.java
git commit -m "add super-admin activation validator and bootstrap-on-boot initializer"
```

---

### Task 4: Super-admin login and magic-link password reset

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminLoginService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminLoginServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminMagicLinkService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminMagicLinkServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/SuperAdminController.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/SuperAdminLoginServiceImplTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/SuperAdminMagicLinkServiceImplTest.java`

**Interfaces:**
- Consumes: `LoginExecutionService.executeLogin(AuthUserEntity, LoginRequest, String, String, long)` / `.handleFailure(UUID, UUID, String, String, String, String)` (existing, unchanged), `LockoutService.checkLockout(String, String)` (existing), `AuthCookieFactory.createAccessTokenCookie`/`createRefreshTokenCookie`/`clearAccessTokenCookie`/`clearRefreshTokenCookie`/`clearLegacyRefreshTokenCookie`/`clearOldNarrowRefreshTokenCookie` (all existing, unchanged), `MagicLinkProperties`/`MagicLinkStore`/`MagicLinkEntry`/`MagicLinkIssueRequest`/`MagicLinkVerifyRequest`/`MagicLinkIssueResponse`/`MagicLinkVerifyResponse`/`MagicLinkInvalidException` (all from the email+magic-link slice, unchanged), `EmailService.sendPasswordResetLink` (existing), `InternalSessionRevocationService.revokeAllForUser(UUID, UUID)` (existing — use this, not direct repo/store calls, learned from the email+magic-link slice's own fix), `TenantConstants.PLATFORM_TENANT_ID` (existing), `PlatformSuperAdminJpaRepository`/`PlatformSuperAdminEntity` (Task 2).
- Produces: `POST /api/v1/auth/super-admin/login`, `POST /api/v1/auth/super-admin/reset-password/issue`, `POST /api/v1/auth/super-admin/reset-password/verify` — all gated by `app.super-admin.enabled=true`.

- [ ] **Step 1: Create `SuperAdminLoginService` interface**

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;

/**
 * Authentication service for the platform-level super admin account.
 * Credentials are stored in {@code platform_super_admin}, separate from
 * tenant user records in {@code auth_users}. Exposed via
 * {@code POST /api/v1/auth/super-admin/login}.
 */
public interface SuperAdminLoginService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);
}
```

- [ ] **Step 2: Create `SuperAdminLoginServiceImpl`**

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.application.service.SuperAdminLoginService;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Authenticates the platform super admin against the {@code platform_super_admin}
 * table. Gated by {@code app.super-admin.enabled=true}.
 *
 * <p>After a successful lookup the entity is mapped to a synthetic
 * {@link AuthUserEntity} and the shared {@link LoginExecutionService} executes
 * the remaining critical path (password verification → session → JWT → Redis →
 * async side-effects) — identical to regular tenant-user login.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminLoginServiceImpl implements SuperAdminLoginService {

    private final PlatformSuperAdminJpaRepository superAdminRepo;
    private final LockoutService                  lockoutService;
    private final LoginExecutionService           loginExecutor;

    @Override
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().toLowerCase().strip();
        long loginStart = System.currentTimeMillis();
        log.info("superadmin.login.started email={} ip={}", email, ipAddress);

        lockoutService.checkLockout(email, ipAddress);

        AuthUserEntity user = superAdminRepo.findByEmailAndActiveTrue(email)
                .map(sa -> AuthUserEntity.builder()
                        .id(sa.getId())
                        .tenantId(TenantConstants.PLATFORM_TENANT_ID)
                        .email(sa.getEmail())
                        .passwordHash(sa.getPasswordHash())
                        .userType(UserType.SUPER_ADMIN)
                        .active(true)
                        .build())
                .orElse(null);

        if (user == null) {
            log.warn("superadmin.login.user_not_found email={} ip={}", email, ipAddress);
            loginExecutor.handleFailure(null, null, email, ipAddress, userAgent, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException();
        }

        MDC.put("userId",   user.getId().toString());
        MDC.put("tenantId", user.getTenantId().toString());
        try {
            return loginExecutor.executeLogin(user, request, ipAddress, userAgent, loginStart);
        } finally {
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }
}
```

- [ ] **Step 3: Create `SuperAdminMagicLinkService` interface**

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;

public interface SuperAdminMagicLinkService {

    MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip);

    MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request);
}
```

- [ ] **Step 4: Create `SuperAdminMagicLinkServiceImpl`**

Note the one deliberate fix versus the pre-trim version: `verify()` calls `InternalSessionRevocationService.revokeAllForUser(userId, tenantId)` instead of the two direct repo/store calls the original had — the same DB-audit-consistency gap the email+magic-link slice found and fixed in its own `MagicLinkServiceImpl`, avoided here from the start.

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.SuperAdminMagicLinkService;
import com.example.authsvc.common.exception.MagicLinkInvalidException;
import com.example.authsvc.common.exception.RateLimitExceededException;
import com.example.authsvc.config.properties.MagicLinkProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminMagicLinkServiceImpl implements SuperAdminMagicLinkService {

    private static final SecureRandom   SECURE_RANDOM = new SecureRandom();
    private static final int            TOKEN_BYTES   = 48;
    private static final Base64.Encoder URL_ENCODER   = Base64.getUrlEncoder().withoutPadding();

    private final MagicLinkProperties             props;
    private final MagicLinkStore                  magicLinkStore;
    private final InternalSessionRevocationService internalSessionRevocationService;
    private final PlatformSuperAdminJpaRepository superAdminRepo;
    private final PasswordHasher                  passwordHasher;
    private final AuditLogService                 auditLogService;
    private final EmailService                    emailService;

    @Override
    public MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip) {
        String normalizedEmail = request.email().trim().toLowerCase();

        String rateLimitKey = "super-admin:" + ip + ":" + RefreshTokenHashUtil.hash(normalizedEmail);
        long attempts = magicLinkStore.incrementRateCounter(rateLimitKey, props.getRateLimitWindow());
        if (attempts > props.getRateLimitMaxRequests()) {
            log.warn("superadmin.magic_link.rate_limited ip={} attempts={}", ip, attempts);
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }

        var superAdminOpt = superAdminRepo.findByEmailAndActiveTrue(normalizedEmail);
        if (superAdminOpt.isEmpty()) {
            log.info("superadmin.magic_link.ignored_unknown_email");
            return MagicLinkIssueResponse.generic();
        }

        var superAdmin = superAdminOpt.get();

        byte[] rawBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawToken  = URL_ENCODER.encodeToString(rawBytes);
        String tokenHash = RefreshTokenHashUtil.hash(rawToken);

        Instant expiresAt = Instant.now().plus(props.getTtl());
        MagicLinkEntry entry = new MagicLinkEntry(
                superAdmin.getId(),
                TenantConstants.PLATFORM_TENANT_ID,
                MagicLinkEntry.PURPOSE_PASSWORD_RESET,
                expiresAt
        );
        magicLinkStore.save(tokenHash, entry, props.getTtl());
        log.info("superadmin.magic_link.issued userId={}", superAdmin.getId());

        String resetUrl = UriComponentsBuilder.fromUriString(props.getFrontendResetUrl())
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendPasswordResetLink(normalizedEmail, resetUrl);

        return MagicLinkIssueResponse.generic();
    }

    @Override
    @Transactional
    public MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request) {
        String tokenHash = RefreshTokenHashUtil.hash(request.token());
        var entryOpt = magicLinkStore.find(tokenHash);

        if (entryOpt.isEmpty()) {
            log.info("superadmin.magic_link.invalid");
            throw new MagicLinkInvalidException();
        }

        MagicLinkEntry entry = entryOpt.get();

        if (Instant.now().isAfter(entry.expiresAt())) {
            magicLinkStore.delete(tokenHash);
            log.info("superadmin.magic_link.expired userId={}", entry.userId());
            throw new MagicLinkInvalidException();
        }

        var superAdmin = superAdminRepo.findById(entry.userId())
                .filter(PlatformSuperAdminEntity::isActive)
                .orElseThrow(() -> {
                    magicLinkStore.delete(tokenHash);
                    log.info("superadmin.magic_link.user_not_found_or_inactive userId={}", entry.userId());
                    return new MagicLinkInvalidException();
                });

        magicLinkStore.delete(tokenHash);

        superAdmin.setPasswordHash(passwordHasher.hash(request.newPassword()));
        superAdminRepo.save(superAdmin);

        UUID userId = superAdmin.getId();
        internalSessionRevocationService.revokeAllForUser(userId, TenantConstants.PLATFORM_TENANT_ID);

        auditLogService.log(new AuditLogRequest(
                TenantConstants.PLATFORM_TENANT_ID,
                userId,
                "superadmin.password.reset.success",
                null,
                null,
                "Super admin password reset via magic link"
        ));

        log.info("superadmin.password.reset.success userId={}", userId);
        return MagicLinkVerifyResponse.success();
    }
}
```

`InternalSessionRevocationService` lives in the same package (`com.example.authsvc.application.impl`) — no import needed, same as the email+magic-link slice's own usage.

- [ ] **Step 5: Create `SuperAdminController`**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;
import com.example.authsvc.application.service.SuperAdminLoginService;
import com.example.authsvc.application.service.SuperAdminMagicLinkService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform super admin login and password reset. Only registered when
 * {@code app.super-admin.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/super-admin")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminLoginService     superAdminLoginService;
    private final SuperAdminMagicLinkService superAdminMagicLinkService;
    private final AuthCookieFactory          cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        long startNs = System.nanoTime();

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        log.debug("superadmin.login.request email={} ip={}", request.getEmail(), ipAddress);

        LoginResult result = superAdminLoginService.login(request, ipAddress, userAgent);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("superadmin.login.response userId={} latencyMs={}",
                result.response().getUserId(), latencyMs);

        ResponseCookie accessCookie = cookieFactory.createAccessTokenCookie(
                result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/reset-password/issue")
    public ResponseEntity<MagicLinkIssueResponse> issueResetPassword(
            @Valid @RequestBody MagicLinkIssueRequest request,
            HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();
        log.debug("superadmin.magic_link.issue.request ip={}", ip);

        MagicLinkIssueResponse response = superAdminMagicLinkService.issue(request, ip);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/reset-password/verify")
    public ResponseEntity<MagicLinkVerifyResponse> verifyResetPassword(
            @Valid @RequestBody MagicLinkVerifyRequest request,
            HttpServletResponse httpResponse) {

        MagicLinkVerifyResponse response = superAdminMagicLinkService.verify(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .body(response);
    }
}
```

- [ ] **Step 6: Add `/api/v1/auth/super-admin/**` to `SecurityConfig`'s permit-list**

Read `SecurityConfig.java` first. Add `"/api/v1/auth/super-admin/**"` to the same `.requestMatchers(...).permitAll()` list that already contains `/api/v1/auth/magic-link/**` (added by the email+magic-link slice) — same public, unauthenticated pattern (the controller does its own credential checking internally).

- [ ] **Step 7: Write `SuperAdminLoginServiceImplTest`**

Read `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java` first to match its conventions — in particular, `LoginRequest` is a plain Lombok `@Data` class with only a no-arg constructor + setters (`new LoginRequest(); request.setEmail(...); request.setPassword(...);`), not a record with an all-args constructor. Then write:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminLoginServiceImplTest {

    @Mock private PlatformSuperAdminJpaRepository superAdminRepo;
    @Mock private LockoutService                  lockoutService;
    @Mock private LoginExecutionService            loginExecutor;

    @InjectMocks
    private SuperAdminLoginServiceImpl service;

    @Test
    void login_unknownEmail_throwsAndRecordsFailure() {
        when(superAdminRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("whatever");

        assertThatThrownBy(() -> service.login(request, "127.0.0.1", "curl/8.0"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginExecutor).handleFailure(null, null, "admin@example.com", "127.0.0.1", "curl/8.0", "INVALID_CREDENTIALS");
    }

    @Test
    void login_knownEmail_delegatesToLoginExecutionService() {
        UUID superAdminId = UUID.randomUUID();
        PlatformSuperAdminEntity superAdmin = PlatformSuperAdminEntity.builder()
                .id(superAdminId).email("admin@example.com").passwordHash("hashed").active(true).build();
        when(superAdminRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.of(superAdmin));

        LoginResult expected = org.mockito.Mockito.mock(LoginResult.class);
        when(loginExecutor.executeLogin(any(), any(), any(), any(), anyLong())).thenReturn(expected);

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("correct horse battery staple");
        LoginResult result = service.login(request, "127.0.0.1", "curl/8.0");

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(expected);
        verify(loginExecutor).executeLogin(any(), org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"), org.mockito.ArgumentMatchers.eq("curl/8.0"), anyLong());
    }
}
```

- [ ] **Step 8: Write `SuperAdminMagicLinkServiceImplTest`**

Mirror the email+magic-link slice's `MagicLinkServiceImplTest` structure (rate limit exceeded, unknown email silently ignored, expired token deletes-and-throws, valid token resets password and calls `internalSessionRevocationService.revokeAllForUser`), substituting `PlatformSuperAdminJpaRepository`/`PlatformSuperAdminEntity` for `AuthUserJpaRepository`/`AuthUserEntity`, and asserting the tenantId passed to `revokeAllForUser` is `TenantConstants.PLATFORM_TENANT_ID` specifically (this is the one behavior specific to the super-admin variant worth its own assertion).

- [ ] **Step 9: Run the tests**

Run: `./gradlew.bat :gen-auth-starter:test --tests "*SuperAdminLoginServiceImplTest" --tests "*SuperAdminMagicLinkServiceImplTest" --console=plain`
Expected: all pass.

Then the full suite: `./gradlew.bat :gen-auth-starter:test --console=plain` → no regressions.

- [ ] **Step 10: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminLoginService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminLoginServiceImpl.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminMagicLinkService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminMagicLinkServiceImpl.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/controller/SuperAdminController.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/SuperAdminLoginServiceImplTest.java \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/SuperAdminMagicLinkServiceImplTest.java
git commit -m "restore super-admin login and magic-link password reset, gated on app.super-admin.enabled"
```

---

### Task 5: Impersonation-token issuance, re-homed onto /internal/auth/**

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/ImpersonationTokenService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ImpersonationTokenController.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/ImpersonationTokenRequest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ImpersonationTokenResponse.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/filter/InternalTokenAuthFilter.java` (javadoc only)
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java`

**Interfaces:**
- Consumes: `JwtUtils.generateAccessToken(JwtClaims)` (existing, unchanged), `TenantSlugResolver.resolve(UUID)` (existing, unchanged), `AuthSessionJpaRepository.save(AuthSessionEntity)` (standard `JpaRepository` method), `UserType.SUPER_ADMIN_IMPERSONATING` (existing enum constant).
- Produces: `POST /internal/auth/impersonation-token` — gated by `app.super-admin.enabled=true` AND protected by the existing `InternalTokenAuthFilter` (`X-Internal-Secret` header), no new auth code.

- [ ] **Step 1: Create `ImpersonationTokenService` interface**

The pre-trim code had no interface for this (direct `ImpersonationTokenServiceImpl` reference from the controller) — added here for consistency with every other service in this codebase.

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;

public interface ImpersonationTokenService {

    ImpersonationTokenResponse issue(ImpersonationTokenRequest request);
}
```

- [ ] **Step 2: Create `ImpersonationTokenServiceImpl`**

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues impersonation JWTs for {@code POST /internal/auth/impersonation-token}.
 * Gated by {@code app.super-admin.enabled=true}. Protected by the existing
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * (shared-secret header) via the {@code /internal/**} path prefix — no bespoke
 * per-request signature scheme, unlike the pre-trim version.
 *
 * <p>The caller is expected to have already resolved the impersonation role ID
 * (and, where available, the tenant slug) through its own authorization flow —
 * this service is a token factory: it records a session locally and signs a JWT,
 * nothing more.
 *
 * <p>Token structure:</p>
 * <pre>
 *   sub         → superAdminId
 *   tenant_id   → impersonated tenant UUID
 *   tenant_slug → impersonated tenant slug
 *   role_id     → impersonation role UUID
 *   user_type   → SUPER_ADMIN_IMPERSONATING
 *   session_id  → caller-supplied correlation ID
 *   exp         → now + jwt.impersonation-token.expiration-minutes (or 240 min if writeConsent)
 * </pre>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImpersonationTokenServiceImpl implements ImpersonationTokenService {

    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final TenantSlugResolver       tenantSlugResolver;

    @Value("${jwt.impersonation-token.expiration-minutes:60}")
    private int expirationMinutes;

    @Override
    @Transactional
    public ImpersonationTokenResponse issue(ImpersonationTokenRequest request) {
        Instant now = Instant.now();
        int ttlMinutes    = request.writeConsent() ? 240 : expirationMinutes;
        Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        String tenantSlug = (request.tenantSlug() != null && !request.tenantSlug().isBlank())
                ? request.tenantSlug()
                : tenantSlugResolver.resolve(request.tenantId());

        log.debug("impersonation.slug.resolved tenantId={} slug={}", request.tenantId(), tenantSlug);

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.superAdminId())
                .tenantId(request.tenantId())
                .roleId(request.impersonationRoleId())
                .userType(UserType.SUPER_ADMIN_IMPERSONATING)
                .impersonation(true)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        JwtClaims claims = new JwtClaims(
                request.superAdminId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.impersonationRoleId()),
                UserType.SUPER_ADMIN_IMPERSONATING,
                now,
                expiresAt,
                request.sessionId(),
                null
        );

        String accessToken = jwtUtils.generateAccessToken(claims);

        int expiresInSeconds = ttlMinutes * 60;
        log.info("impersonation.token.issued superAdminId={} tenantId={} sessionId={} expiresIn={}",
                request.superAdminId(), request.tenantId(), request.sessionId(), expiresInSeconds);
        return new ImpersonationTokenResponse(accessToken, expiresInSeconds, request.sessionId());
    }
}
```

- [ ] **Step 3: Create the request/response DTOs**

```java
package com.example.authsvc.api.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /internal/auth/impersonation-token}.
 *
 * <p>The calling internal service is expected to have already resolved
 * {@code impersonationRoleId} (and, where available, {@code tenantSlug})
 * through its own authorization flow before calling this endpoint — this
 * service is a pure token factory: it receives pre-validated data, records
 * a session, and issues a JWT.
 */
public record ImpersonationTokenRequest(

        /** UUID of the super admin initiating the impersonation. Becomes JWT {@code sub}. */
        @NotNull UUID superAdminId,

        /** UUID of the impersonated tenant. Becomes JWT {@code tenant_id}. */
        @NotNull UUID tenantId,

        /**
         * Slug of the impersonated tenant, if the caller already has it; may be
         * blank, in which case {@link com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver}
         * resolves it instead.
         */
        @Nullable String tenantSlug,

        /**
         * UUID of the impersonation role for the tenant, resolved by the caller.
         * Becomes JWT {@code role_id}.
         */
        @NotNull UUID impersonationRoleId,

        /**
         * Caller-supplied correlation ID. Stored on the auth session and
         * embedded as JWT {@code session_id}.
         */
        @NotBlank String sessionId,

        /**
         * Whether write access was requested and consented to. Currently stored
         * for audit and to extend the token TTL; the impersonation role itself
         * enforces read-only permissions regardless of this value.
         */
        boolean writeConsent
) {}
```

```java
package com.example.authsvc.api.dto.response;

/**
 * Response from {@code POST /internal/auth/impersonation-token}.
 *
 * @param accessToken the signed RS256 JWT with {@code user_type=SUPER_ADMIN_IMPERSONATING}
 * @param expiresIn   token lifetime in seconds
 * @param sessionId   the caller-supplied correlation ID, echoed back
 */
public record ImpersonationTokenResponse(
        String accessToken,
        int    expiresIn,
        String sessionId
) {}
```

- [ ] **Step 4: Create `ImpersonationTokenController`**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for issuing impersonation JWTs. Only registered when
 * {@code app.super-admin.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix — the same mechanism already
 * guarding {@code /internal/auth/users} and {@code /internal/auth/keys/**}.
 * No user JWT is required or checked.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImpersonationTokenController {

    private final ImpersonationTokenService impersonationTokenService;

    @PostMapping("/impersonation-token")
    public ResponseEntity<ImpersonationTokenResponse> issueImpersonationToken(
            @Valid @RequestBody ImpersonationTokenRequest request) {

        log.info("impersonation.token.request superAdminId={} tenantId={} sessionId={}",
                request.superAdminId(), request.tenantId(), request.sessionId());

        ImpersonationTokenResponse response = impersonationTokenService.issue(request);

        return ResponseEntity.ok(response);
    }
}
```

No `SecurityConfig` change needed — `/internal/**` is already `permitAll()` + guarded by `InternalTokenAuthFilter`.

- [ ] **Step 5: Update `InternalTokenAuthFilter`'s stale javadoc**

Its current javadoc says:

```java
 * <p>This is intentionally simpler than the HMAC filter used for
 * {@code /v1/impersonation-token}: internal service endpoints are called
 * over a private network by trusted services, not by external clients, so a
 * shared secret (rather than a per-request HMAC signature) is sufficient.
```

Replace with:

```java
 * <p>This also guards {@code /internal/auth/impersonation-token} — a shared
 * secret is sufficient since these endpoints are called over a private
 * network by trusted internal services, not external clients.
```

- [ ] **Step 6: Write `ImpersonationTokenServiceImplTest`**

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpersonationTokenServiceImplTest {

    @Mock private JwtUtils                 jwtUtils;
    @Mock private AuthSessionJpaRepository authSessionRepository;
    @Mock private TenantSlugResolver       tenantSlugResolver;

    @Test
    void issue_readOnly_usesConfiguredTtlAndResolvesSlugViaFallback() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, null, roleId, "session-abc", false);

        ImpersonationTokenResponse response = service.issue(request);

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(60 * 60);
        assertThat(response.sessionId()).isEqualTo("session-abc");

        ArgumentCaptor<AuthSessionEntity> captor = ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(authSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.SUPER_ADMIN_IMPERSONATING);
        assertThat(captor.getValue().isImpersonation()).isTrue();
    }

    @Test
    void issue_writeConsent_usesExtendedFourHourTtl() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, null, roleId, "session-abc", true);

        ImpersonationTokenResponse response = service.issue(request);

        assertThat(response.expiresIn()).isEqualTo(240 * 60);
    }

    @Test
    void issue_tenantSlugProvided_skipsResolverFallback() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        verify(tenantSlugResolver, org.mockito.Mockito.never()).resolve(any());
    }

    private static void setExpirationMinutes(ImpersonationTokenServiceImpl service, int value) throws Exception {
        var field = ImpersonationTokenServiceImpl.class.getDeclaredField("expirationMinutes");
        field.setAccessible(true);
        field.set(service, value);
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew.bat :gen-auth-starter:test --tests "*ImpersonationTokenServiceImplTest" --console=plain`
Expected: all 3 pass.

Then the full suite: `./gradlew.bat :gen-auth-starter:test --console=plain` → no regressions.

- [ ] **Step 8: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/service/ImpersonationTokenService.java \
        gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ImpersonationTokenController.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/ImpersonationTokenRequest.java \
        gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ImpersonationTokenResponse.java \
        gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/filter/InternalTokenAuthFilter.java \
        gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java
git commit -m "restore impersonation-token issuance, re-homed onto /internal/auth/** with existing shared-secret auth"
```

---

### Task 6: Demo config, docs, and manual verification

**Files:**
- Modify: `gen-auth-demo/src/main/resources/application.yaml`
- Modify: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: a documented, runnable manual-verification path.

- [ ] **Step 1: Add example config to `gen-auth-demo/src/main/resources/application.yaml`**

Add this block, disabled by default, near the existing `app.email`/`app.magic-link` block added by the email+magic-link slice:

```yaml
# ===================================================================
# SUPER ADMIN + IMPERSONATION (optional — off by default)
# ===================================================================
# Requires app.email.enabled=true too (bootstrap + reset-password emails).
# To try this locally: flip both flags true, set SUPER_ADMIN_EMAIL/PASSWORD,
# check Mailhog at http://localhost:8026 for the bootstrap-credentials email.

app:
  super-admin:
    enabled: false

auth:
  super-admin:
    email: ${SUPER_ADMIN_EMAIL:}
    password: ${SUPER_ADMIN_PASSWORD:}
```

- [ ] **Step 2: Boot the demo with super-admin (and email) enabled, verify for real**

```bash
docker compose up -d   # postgres, redis, mailhog
```

```bash
APP_EMAIL_ENABLED=true APP_MAGIC_LINK_ENABLED=true APP_SUPER_ADMIN_ENABLED=true \
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC \
AUTH_DB_URL=jdbc:postgresql://localhost:5433/genauth \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
SPRING_DATA_REDIS_PORT=6380 \
INTERNAL_SERVICE_SECRET=dev-secret \
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
JWT_ISSUER=genauth JWT_AUDIENCE=genauth \
SUPER_ADMIN_EMAIL=admin@example.com SUPER_ADMIN_PASSWORD=TempPass123! \
./gradlew.bat :gen-auth-demo:bootRun
```

Confirm in the boot log: `superadmin.bootstrap.created email=admin@example.com`.

Check Mailhog (`curl http://localhost:8026/api/v2/messages`) for the bootstrap-credentials email — confirm subject "Your super admin account is ready" and the password appears in the body.

```bash
curl -i -X POST http://localhost:8101/api/v1/auth/super-admin/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"TempPass123!"}'
# expect: 200, Set-Cookie headers
```

Request and complete a super-admin password reset the same way the email+magic-link slice's own manual verification did (`/api/v1/auth/super-admin/reset-password/issue` → check Mailhog → `/api/v1/auth/super-admin/reset-password/verify`), then confirm login with the new password.

Issue an impersonation token:

```bash
curl -i -X POST http://localhost:8101/internal/auth/impersonation-token \
  -H 'Content-Type: application/json' \
  -H 'X-Internal-Secret: dev-secret' \
  -d '{
    "superAdminId": "00000000-0000-0000-0000-000000000001",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "tenantSlug": "acme",
    "impersonationRoleId": "22222222-2222-2222-2222-222222222222",
    "sessionId": "manual-test-session",
    "writeConsent": false
  }'
# expect: 200, a signed JWT, expiresIn=3600

curl -i -X POST http://localhost:8101/internal/auth/impersonation-token \
  -H 'Content-Type: application/json' \
  -d '{...same body...}'
# expect: 401 — missing X-Internal-Secret
```

- [ ] **Step 3: Verify off-by-default and the fail-fast cross-dependency**

Reboot with `APP_SUPER_ADMIN_ENABLED` unset (or false) and `APP_EMAIL_ENABLED=true`: confirm `/api/v1/auth/super-admin/login` and `/internal/auth/impersonation-token` are both absent (no route registered), health stays `UP`.

Reboot with `APP_SUPER_ADMIN_ENABLED=true` and `APP_EMAIL_ENABLED` unset: confirm the app refuses to boot with `SuperAdminActivationValidator`'s `IllegalStateException` message visible.

- [ ] **Step 4: Update `README.md`**

Document `app.super-admin.enabled`, `auth.super-admin.email`/`.password` (env vars `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD`), the cross-dependency on `app.email.enabled`, and the new `POST /internal/auth/impersonation-token` endpoint (with its `X-Internal-Secret` requirement) in the existing environment-variables and internal-endpoints tables. Note in the module-layout/"what this is" section that super-admin login, bootstrap, magic-link reset, and impersonation-token issuance are now restored (optional).

- [ ] **Step 5: Commit**

```bash
git add gen-auth-demo/src/main/resources/application.yaml README.md
git commit -m "document optional super-admin/impersonation config, verify end-to-end against real infra"
```
