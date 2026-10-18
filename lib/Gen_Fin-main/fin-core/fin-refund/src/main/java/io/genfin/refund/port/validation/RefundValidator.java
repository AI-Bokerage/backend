package io.genfin.refund.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationResult;

/**
 * Validates a {@link Refund} itself — amount, currency, balance, duplication, reason and window
 * checks — as distinct from {@link RefundRequestValidator}, which validates the {@code
 * RefundRequest} that preceded it.
 */
public interface RefundValidator extends Extension {

  ValidationResult validate(Refund refund, ValidationContext context);
}
