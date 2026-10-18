package com.example.tnt_svc.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugValidatorTest {

    private final SlugValidator validator = new SlugValidator();

    @Test
    void acceptsLowercaseAlphanumericWithHyphens() {
        assertThat(validator.isValid("acme-corp", null)).isTrue();
        assertThat(validator.isValid("abc", null)).isTrue();
    }

    @Test
    void rejectsUppercase() {
        assertThat(validator.isValid("Acme", null)).isFalse();
    }

    @Test
    void rejectsLeadingDigit() {
        assertThat(validator.isValid("1acme", null)).isFalse();
    }

    @Test
    void rejectsTrailingHyphen() {
        assertThat(validator.isValid("acme-", null)).isFalse();
    }

    @Test
    void rejectsTooShort() {
        assertThat(validator.isValid("ab", null)).isFalse();
    }

    @Test
    void rejectsTooLong() {
        assertThat(validator.isValid("a".repeat(64), null)).isFalse();
    }

    @Test
    void acceptsMinAndMaxLength() {
        assertThat(validator.isValid("abc", null)).isTrue();
        assertThat(validator.isValid("a" + "b".repeat(61) + "c", null)).isTrue();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
