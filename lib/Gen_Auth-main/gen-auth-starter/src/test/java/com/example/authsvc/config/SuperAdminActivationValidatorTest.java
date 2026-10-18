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
        setMagicLinkEnabled(validator, true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.super-admin.enabled=true requires app.email.enabled=true");
    }

    @Test
    void validate_magicLinkDisabled_throws() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("admin@example.com");
        props.setPassword("TempPass123!");
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, true);
        setMagicLinkEnabled(validator, false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.super-admin.enabled=true requires app.magic-link.enabled=true");
    }

    @Test
    void validate_blankEmail_throws() throws Exception {
        SuperAdminProperties props = new SuperAdminProperties();
        props.setEmail("");
        props.setPassword("TempPass123!");
        SuperAdminActivationValidator validator = new SuperAdminActivationValidator(props);
        setEmailEnabled(validator, true);
        setMagicLinkEnabled(validator, true);

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
        setMagicLinkEnabled(validator, true);

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
        setMagicLinkEnabled(validator, true);

        validator.validate(); // must not throw
    }

    private static void setEmailEnabled(SuperAdminActivationValidator validator, boolean value) throws Exception {
        setField(validator, "emailEnabled", value);
    }

    private static void setMagicLinkEnabled(SuperAdminActivationValidator validator, boolean value) throws Exception {
        setField(validator, "magicLinkEnabled", value);
    }

    private static void setField(SuperAdminActivationValidator validator, String fieldName, boolean value) throws Exception {
        var field = SuperAdminActivationValidator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(validator, value);
    }
}
