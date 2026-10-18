package com.example.authsvc.config;

import com.example.authsvc.config.properties.SuperAdminProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails startup fast if the super-admin bucket (login, bootstrap, magic-link
 * reset, impersonation-token issuance) is enabled without its hard
 * requirements all being satisfied:
 * <ol>
 *   <li>{@code app.email.enabled=true} — bootstrap-credential and password-reset
 *       emails both need it.</li>
 *   <li>{@code app.magic-link.enabled=true} — the super-admin password-reset
 *       flow ({@code SuperAdminMagicLinkServiceImpl}) reuses the same
 *       Redis-backed {@code MagicLinkStore} infrastructure as the tenant-user
 *       magic-link flow, and that bean only exists when this flag is set.</li>
 *   <li>{@code auth.super-admin.email} / {@code auth.super-admin.password} both
 *       set (non-blank).</li>
 * </ol>
 *
 * <p>Only instantiated when {@code app.super-admin.enabled=true}. This is the
 * same shape of bean-graph dependency that {@code MagicLinkActivationValidator}
 * guards against in the email+magic-link slice: {@code SuperAdminProperties},
 * {@code PlatformSuperAdminJpaRepository}, and {@code PasswordHasher} are
 * always registered regardless of configuration, but {@code MagicLinkStore}
 * (required by {@code SuperAdminMagicLinkServiceImpl}) is conditional on
 * {@code app.magic-link.enabled}. Both flag checks below exist precisely so
 * that missing-bean race surfaces as this validator's friendly message
 * instead of an {@code UnsatisfiedDependencyException} deep in bean creation.
 */
@Component
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminActivationValidator {

    private final SuperAdminProperties superAdminProperties;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.magic-link.enabled:false}")
    private boolean magicLinkEnabled;

    @PostConstruct
    public void validate() {
        if (!emailEnabled) {
            throw new IllegalStateException(
                    "app.super-admin.enabled=true requires app.email.enabled=true — " +
                    "bootstrap-credential and password-reset emails cannot be delivered without it.");
        }
        if (!magicLinkEnabled) {
            throw new IllegalStateException(
                    "app.super-admin.enabled=true requires app.magic-link.enabled=true — " +
                    "the super-admin password-reset flow reuses the same Redis-backed magic-link " +
                    "infrastructure as the tenant-user flow, and its MagicLinkStore bean is not " +
                    "registered without it.");
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
