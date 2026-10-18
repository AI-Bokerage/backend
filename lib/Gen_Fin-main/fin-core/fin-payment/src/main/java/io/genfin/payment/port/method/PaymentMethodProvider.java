package io.genfin.payment.port.method;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.method.PaymentMethodDescriptor;
import java.util.List;

public interface PaymentMethodProvider extends Extension {

  List<PaymentMethodDescriptor> provide();
}
