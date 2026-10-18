package com.example.authsvc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpActivationValidatorTest {

    @Test
    void validate_emailDisabled_throwsIllegalState() throws Exception {
        OtpActivationValidator validator = new OtpActivationValidator();
        var field = OtpActivationValidator.class.getDeclaredField("emailEnabled");
        field.setAccessible(true);
        field.set(validator, false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.otp.enabled=true requires app.email.enabled=true");
    }

    @Test
    void validate_emailEnabled_doesNotThrow() throws Exception {
        OtpActivationValidator validator = new OtpActivationValidator();
        var field = OtpActivationValidator.class.getDeclaredField("emailEnabled");
        field.setAccessible(true);
        field.set(validator, true);

        validator.validate(); // must not throw
    }
}
