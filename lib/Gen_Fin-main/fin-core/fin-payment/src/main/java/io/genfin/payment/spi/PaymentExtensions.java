package io.genfin.payment.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.payment.authorization.AuthorizationStrategies;
import io.genfin.payment.calculation.PaymentCalculators;
import io.genfin.payment.gateway.GatewayRegistries;
import io.genfin.payment.idempotency.IdempotencyValidators;
import io.genfin.payment.lifecycle.PaymentLifecycles;
import io.genfin.payment.method.PaymentMethodRegistries;
import io.genfin.payment.port.authorization.AuthorizationStrategy;
import io.genfin.payment.port.authorization.CaptureStrategy;
import io.genfin.payment.port.calculation.PaymentCalculator;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.idempotency.IdempotencyValidator;
import io.genfin.payment.port.lifecycle.LifecycleProvider;
import io.genfin.payment.port.method.PaymentMethodProvider;
import io.genfin.payment.port.validation.PaymentValidator;
import io.genfin.payment.validation.PaymentValidators;

/**
 * Registers every default Payment-engine extension so downstream code discovers them through one
 * mechanism.
 */
public final class PaymentExtensions {

  private PaymentExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(LifecycleProvider.class, PaymentLifecycles.standard());
    registry.register(PaymentCalculator.class, PaymentCalculators.standard());
    registry.register(PaymentValidator.class, PaymentValidators.standard());
    registry.register(PaymentMethodProvider.class, PaymentMethodRegistries.standardCatalog());
    registry.register(GatewaySelector.class, GatewayRegistries.firstCapable());
    registry.register(CaptureStrategy.class, AuthorizationStrategies.automaticCapture());
    registry.register(AuthorizationStrategy.class, AuthorizationStrategies.fullAuthorization());
    registry.register(IdempotencyValidator.class, IdempotencyValidators.standard());
  }
}
