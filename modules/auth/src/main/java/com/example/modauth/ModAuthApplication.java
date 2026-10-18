package com.example.modauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Host application for this repo's auth module. gen-auth-starter's
 * {@code GenAuthAutoConfiguration} supplies login, session, lockout, JWT and
 * forgot-password (magic-link) end to end; the classes under this package
 * add only what that starter doesn't cover — role hierarchy, terms-of-service
 * gating on login, and the invitation lifecycle — reusing the starter's own
 * beans (PasswordHasher, LockoutService, LoginExecutionService, JwtAuthenticationFilter, ...)
 * rather than re-implementing them.
 *
 * <p>{@code @EnableJpaRepositories} here is required, not redundant with Boot's
 * own default repository scan: once GenAuthAutoConfiguration's explicit
 * {@code @EnableJpaRepositories("com.example.authsvc...")} is on the classpath,
 * Boot's implicit auto-scan backs off entirely (it's an all-or-nothing
 * {@code @ConditionalOnMissingBean}), so this module's own repositories under
 * {@code com.example.modauth.repository} would otherwise never get a bean —
 * confirmed by an actual boot failure ("No qualifying bean of type
 * InvitationJpaRepository") before this annotation was added.
 *
 * <p>{@code @EntityScan} here is for the same reason, on the entity side:
 * confirmed by an actual boot failure ("Not a managed type: class
 * com.example.modauth.entity.InvitationEntity") before this was added.
 * Unlike {@code @EnableJpaRepositories}, Boot does merge multiple
 * {@code @EntityScan} package lists together, so this one adds to — rather
 * than replacing — the starter's own.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.modauth.repository")
@EntityScan(basePackages = "com.example.modauth.entity")
public class ModAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModAuthApplication.class, args);
    }
}
