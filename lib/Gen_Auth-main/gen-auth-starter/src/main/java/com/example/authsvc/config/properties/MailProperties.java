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
