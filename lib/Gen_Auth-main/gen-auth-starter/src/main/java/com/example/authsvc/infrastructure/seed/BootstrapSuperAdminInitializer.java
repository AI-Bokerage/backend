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
 * this must never run in a partially initialised state — except best-effort
 * bootstrap-email delivery, which is logged and swallowed rather than failing
 * boot.
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
