package com.example.authsvc.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails startup fast if OTP is enabled without email also being enabled —
 * an OTP code with no way to be delivered is a broken feature, not a
 * partial one. Only instantiated when {@code app.otp.enabled=true}; if
 * email is also enabled the check passes silently.
 */
@Component
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OtpActivationValidator {

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @PostConstruct
    public void validate() {
        if (!emailEnabled) {
            throw new IllegalStateException(
                    "app.otp.enabled=true requires app.email.enabled=true — " +
                    "OTP codes have no way to be delivered without email sending turned on.");
        }
    }
}
