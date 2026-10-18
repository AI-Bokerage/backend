package com.example.tnt_svc.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SlugValidator.class)
public @interface ValidSlug {
    String message() default "slug must be lowercase alphanumeric with hyphens, 3-63 characters, not starting/ending with a hyphen";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
