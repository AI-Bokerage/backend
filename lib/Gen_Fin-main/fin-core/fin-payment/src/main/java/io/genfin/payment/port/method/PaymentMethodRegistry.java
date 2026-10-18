package io.genfin.payment.port.method;

import io.genfin.payment.method.PaymentMethodDescriptor;
import io.genfin.payment.method.PaymentMethodType;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRegistry {

  void register(PaymentMethodDescriptor descriptor);

  Optional<PaymentMethodDescriptor> find(PaymentMethodType type);

  default PaymentMethodDescriptor require(PaymentMethodType type) {
    return find(type)
        .orElseThrow(
            () -> new io.genfin.payment.exception.UnsupportedPaymentMethodException(type.code()));
  }

  List<PaymentMethodDescriptor> findAll();
}
