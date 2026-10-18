package com.company.audit.spring.config;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a property, if the consuming application set it at all, was not set to a
 * blank/whitespace-only value — but {@code null} (never set) always passes.
 *
 * <p>Deliberately not {@code @NotBlank}, which rejects {@code null} too: a property such as
 * {@link AuditProperties.Anchor#getBucket()} has no default precisely because most applications
 * never intend to use the feature it configures at all, and must remain unset (and therefore
 * {@code null}) without failing startup. The failure this constraint exists to catch is narrower
 * and different — a consuming application that <em>did</em> set the property, but to an empty or
 * whitespace-only value, which would otherwise surface much later as an unrelated, unclear
 * failure deep inside whatever adapter actually uses it (for {@code audit.anchor.bucket}, an AWS
 * region-resolution error inside {@code S3ObjectLockAdapter}, confirmed by an actual failure, not
 * assumed) — this constraint catches it at startup instead, with a message naming the property.
 */
@Documented
@Constraint(validatedBy = NotBlankIfPresent.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlankIfPresent {

    /**
     * The message reported when validation fails.
     *
     * @return the message template
     */
    String message() default "must not be blank if set";

    /**
     * Validation groups this constraint belongs to.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload associated with this constraint.
     *
     * @return the payload types
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * The actual validation logic: {@code null} always passes, a blank/whitespace-only string
     * does not.
     */
    class Validator implements ConstraintValidator<NotBlankIfPresent, String> {

        /**
         * Creates a new validator.
         */
        public Validator() {
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || !value.isBlank();
        }
    }
}
