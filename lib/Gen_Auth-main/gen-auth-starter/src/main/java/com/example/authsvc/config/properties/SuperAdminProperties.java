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
