package com.example.tnt_svc.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Ported as-is from tnt-svc — confirmed zero CPMS coupling in the genericization audit. */
public class SlugValidator implements ConstraintValidator<ValidSlug, String> {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*[a-z0-9]$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        if (value.length() < 3 || value.length() > 63) {
            return false;
        }
        return SLUG_PATTERN.matcher(value).matches();
    }
}
