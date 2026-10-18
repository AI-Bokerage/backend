package com.example.authsvc.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordsMatchValidator
        implements ConstraintValidator<PasswordsMatch, PasswordConfirmationRequest> {

    @Override
    public boolean isValid(PasswordConfirmationRequest request, ConstraintValidatorContext ctx) {
        if (request == null) return true; // let @NotNull / @NotBlank handle nulls
        if (request.newPassword() == null || request.confirmPassword() == null) return true;

        boolean match = request.newPassword().equals(request.confirmPassword());
        if (!match) {
            // Attach the error to the confirmPassword field for a clear API error message
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
               .addPropertyNode("confirmPassword")
               .addConstraintViolation();
        }
        return match;
    }
}
