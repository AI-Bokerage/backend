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
