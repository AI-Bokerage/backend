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
